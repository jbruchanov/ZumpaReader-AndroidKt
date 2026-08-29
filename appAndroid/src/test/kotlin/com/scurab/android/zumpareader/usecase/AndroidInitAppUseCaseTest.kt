package com.scurab.android.zumpareader.usecase

import androidx.core.app.NotificationManagerCompat
import com.scurab.android.zumpareader.component.NotificationStateProvider
import com.scurab.android.zumpareader.repository.AnalyticsEvent
import com.scurab.android.zumpareader.repository.AnalyticsReporter
import com.scurab.android.zumpareader.repository.AnalyticsUserProperty
import com.scurab.android.zumpareader.repository.CrashReporter
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.util.KeyValueStore
import com.scurab.android.zumpareader.util.ZumpaPrefs
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The crash-report identity, which is the one thing here nothing else would notice was wrong: a
 * report filed under the wrong id looks exactly like a report filed under the right one until
 * somebody goes looking in the console for a name that is not there.
 *
 * The notification user properties are the same kind of thing one step removed. They are what every
 * push number gets segmented by, so a wrong one does not look wrong - it makes something else look
 * wrong.
 *
 * `runBlocking` rather than `runTest`: [ZumpaSettingsRepository] shares its flows on a scope of its
 * own on `Dispatchers.Default`, which virtual time cannot advance - so a write has to be waited for
 * in real time. The two cases that read the value a `StateFlow` already holds need no wait at all.
 */
class AndroidInitAppUseCaseTest {

    private val store = FakeKeyValueStore()
    private val prefs = ZumpaPrefs(store)
    private val reported = mutableListOf<String>()
    private val crashReporter = object : CrashReporter {
        override fun setUserId(userId: String) {
            reported += userId
        }
    }
    private val channels = mockk<CreateNotificationChannelsUseCase>().also { justRun { it() } }
    private val analytics = RecordingAnalyticsReporter()

    /** Permitted and at the importance the app asks for, unless a case below says otherwise. */
    private val notifications = mockk<NotificationStateProvider>().also {
        every { it.hasNotificationsPermissionGranted() } returns true
        every { it.channelImportance(any()) } returns NotificationManagerCompat.IMPORTANCE_DEFAULT
    }

    /** Unconfined, so the collector runs the moment it is launched rather than a dispatch later. */
    private fun useCase() = AndroidInitAppUseCase(
        createNotificationChannels = channels,
        //built here, so a test that has already written to prefs gets a flow that starts from it
        settings = ZumpaSettingsRepository(prefs),
        crashReporter = crashReporter,
        notifications = notifications,
        analytics = analytics,
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    /**
     * The launch of a session signed in during an earlier run. No waiting: `loggedUserName` is a
     * `StateFlow`, so the collector is handed the current value rather than the next change.
     */
    @Test
    fun `a session that was already signed in reports crashes under its user name`() {
        prefs.setUserName("honza")
        prefs.isLoggedIn = true

        useCase()()

        assertEquals(listOf("honza"), reported)
    }

    @Test
    fun `an anonymous launch is identified by the per install id`() {
        useCase()()

        assertEquals(listOf(prefs.userId), reported)
    }

    @Test
    fun `signing in attaches the user name without another launch`() = runBlocking {
        useCase()()

        prefs.setUserName("honza")
        prefs.isLoggedIn = true

        assertEquals("honza", awaitReported { it == "honza" })
    }

    /** The regression in [ZumpaPrefs.userId]: this used to leave no id at all until a relaunch. */
    @Test
    fun `signing out puts the anonymous id back rather than leaving none`() = runBlocking {
        prefs.setUserName("honza")
        prefs.isLoggedIn = true
        useCase()()

        prefs.isLoggedIn = false

        val id = awaitReported { it != "honza" }
        assertNotEquals("honza", id)
        assertTrue(id.isNotEmpty())
    }

    @Test
    fun `the notification channels are created`() {
        useCase()()

        verify(exactly = 1) { channels() }
    }

    @Test
    fun `an install that may be notified says so`() {
        useCase()()

        assertEquals("true", analytics.properties[AnalyticsUserProperty.NotificationsEnabled])
    }

    /** A refused permission is the innocent explanation for an install that gets no pushes. */
    @Test
    fun `an install that may not be notified says so`() {
        every { notifications.hasNotificationsPermissionGranted() } returns false

        useCase()()

        assertEquals("false", analytics.properties[AnalyticsUserProperty.NotificationsEnabled])
    }

    /** A word rather than the 3 the platform calls it - the console groups by the value. */
    @Test
    fun `the channel importance is reported by name`() {
        useCase()()

        assertEquals("default", analytics.properties[AnalyticsUserProperty.ChannelImportance])
    }

    /**
     * The user quietening the channel themselves. Told apart from a refused permission on purpose:
     * both come out as an install that never makes a sound.
     */
    @Test
    fun `a channel the user has turned down is reported at the importance it now has`() {
        every {
            notifications.channelImportance(any())
        } returns NotificationManagerCompat.IMPORTANCE_NONE

        useCase()()

        assertEquals("none", analytics.properties[AnalyticsUserProperty.ChannelImportance])
    }

    /** Not `none` - no channel and a channel switched off are different states. */
    @Test
    fun `no channel at all is reported as missing rather than as silent`() {
        every { notifications.channelImportance(any()) } returns null

        useCase()()

        assertEquals("missing", analytics.properties[AnalyticsUserProperty.ChannelImportance])
    }

    /** On a first run there is nothing to read an importance off until the channel is made. */
    @Test
    fun `the channel is created before its importance is read`() {
        useCase()()

        verifyOrder {
            channels()
            notifications.channelImportance(any())
        }
    }

    /** Nothing at startup logs an event - the properties are all this use case has to say. */
    @Test
    fun `startup reports no events of its own`() {
        useCase()()

        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    /** The last reported id once it satisfies [predicate], or a failure once the wait runs out. */
    private suspend fun awaitReported(predicate: (String) -> Boolean): String =
        withTimeoutOrNull(AWAIT_TIMEOUT_MS) {
            while (reported.lastOrNull()?.let(predicate) != true) {
                delay(POLL_MS)
            }
            reported.last()
        } ?: error("nothing matching was reported - saw $reported")
}

private const val AWAIT_TIMEOUT_MS = 2_000L
private const val POLL_MS = 5L

private class RecordingAnalyticsReporter : AnalyticsReporter {
    val events = mutableListOf<AnalyticsEvent>()
    val properties = mutableMapOf<AnalyticsUserProperty, String>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(property: AnalyticsUserProperty, value: String) {
        properties[property] = value
    }
}

/** `InMemoryKeyValueStore` lives in `:shared`'s jvm target, which `:appAndroid` does not see. */
private class FakeKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any?>()
    private val _changes = MutableSharedFlow<String?>(extraBufferCapacity = 64)

    override val changes: Flow<String?> = _changes

    override fun getString(key: String, default: String?): String? =
        values[key] as? String ?: default

    override fun putString(key: String, value: String?) = put(key, value)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        values[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) = put(key, value)

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String>? = values[key] as? Set<String>

    override fun putStringSet(key: String, value: Set<String>?) = put(key, value)

    private fun put(key: String, value: Any?) {
        values[key] = value
        _changes.tryEmit(key)
    }
}
