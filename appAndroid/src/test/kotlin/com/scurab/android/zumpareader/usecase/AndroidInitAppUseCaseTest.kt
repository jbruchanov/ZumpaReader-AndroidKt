package com.scurab.android.zumpareader.usecase

import com.scurab.android.zumpareader.repository.CrashReporter
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.util.KeyValueStore
import com.scurab.android.zumpareader.util.ZumpaPrefs
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
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

    /** Unconfined, so the collector runs the moment it is launched rather than a dispatch later. */
    private fun useCase() = AndroidInitAppUseCase(
        createNotificationChannels = channels,
        //built here, so a test that has already written to prefs gets a flow that starts from it
        settings = ZumpaSettingsRepository(prefs),
        crashReporter = crashReporter,
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
