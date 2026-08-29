package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.ZumpaPHPAPI
import com.scurab.android.zumpareader.model.ZumpaGenericResponse
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.InMemoryKeyValueStore
import com.scurab.android.zumpareader.util.ZumpaPrefs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The push token half.
 *
 * Worth pinning because none of it is visible when it goes wrong: a token that never reaches the
 * forum looks exactly like a forum with nothing to say. The refresh path used to be a second copy
 * of this living in `MyFirebaseService`, and it had drifted - it read the online/offline api switch
 * rather than the online api, so a refresh while offline mode was on never found a uid and gave up
 * silently, and it never stored the token it registered.
 *
 * The same invisibility is why every outcome is reported, and why each case here asserts the
 * reported one as well as the boolean. The two come out of a single decision in `report` so that
 * they cannot disagree, and this is what holds them to it - a wrong outcome would leave the console
 * saying registrations succeed while installs get no pushes, which is the failure this is all for.
 */
class AuthRepositoryTest {

    private val store = InMemoryKeyValueStore()
    private val prefs = ZumpaPrefs(store)

    private val onlineApi = mockk<ZumpaAPI>()
    private val phpApi = mockk<ZumpaPHPAPI>()
    private val analytics = RecordingAnalyticsReporter()

    private fun repository() = AuthRepository(
        onlineApi = onlineApi,
        phpApi = phpApi,
        prefs = prefs,
        parser = ZumpaSimpleParser(),
        cookies = mockk<CookieRepository>(relaxed = true),
        pushTokens = NoPushTokenProvider,
        analytics = analytics,
    )

    private fun signIn() {
        prefs.setUserName("honza")
        prefs.isLoggedIn = true
    }

    /** The shape USER_ID_PATTERN looks for - a profile link, closing quote included. */
    private fun mainPageWith(uid: String) = respondWith(onlineApi, "<a href='profile.php?uid=$uid'>")

    private fun phpAnswers(body: String) {
        coEvery { phpApi.register(any(), any(), any()) } returns response(body)
    }

    private fun respondWith(api: ZumpaAPI, html: String) {
        coEvery { api.getMainPageHtml() } returns response(html)
    }

    private fun response(body: String) = ZumpaGenericResponse(body.toByteArray(), contentType = null)

    /** Exactly one registration reported - a second would double every count in the console. */
    private fun assertReported(
        source: PushRegistrationSource,
        outcome: PushRegistrationOutcome,
    ) = assertEquals(
        listOf(AnalyticsEvent.PushRegistration(source, outcome)),
        analytics.events,
    )

    @Test
    fun `a refreshed token is registered against the signed-in user`() = runTest {
        signIn()
        mainPageWith("abc123")
        phpAnswers("[OK]")

        val result = repository().onPushTokenRefreshed("the-new-token")

        assertTrue(result)
        coVerify { phpApi.register("honza", "abc123", "the-new-token") }
        assertReported(PushRegistrationSource.TokenRefresh, PushRegistrationOutcome.Ok)
    }

    /** It stored nothing before, so a later login had no idea which token was live. */
    @Test
    fun `a refreshed token is stored`() = runTest {
        signIn()
        mainPageWith("abc123")
        phpAnswers("[OK]")

        repository().onPushTokenRefreshed("the-new-token")

        assertEquals("the-new-token", prefs.pushRegId)
    }

    @Test
    fun `there is nobody to register a refreshed token for when signed out`() = runTest {
        val result = repository().onPushTokenRefreshed("the-new-token")

        assertFalse(result)
        assertNull(prefs.pushRegId)
        coVerify(exactly = 0) { phpApi.register(any(), any(), any()) }
        assertReported(PushRegistrationSource.TokenRefresh, PushRegistrationOutcome.NoUser)
    }

    @Test
    fun `a forum that does not answer OK is not a registration`() = runTest {
        signIn()
        mainPageWith("abc123")
        phpAnswers("nope")

        assertFalse(repository().onPushTokenRefreshed("the-new-token"))
        assertReported(PushRegistrationSource.TokenRefresh, PushRegistrationOutcome.Rejected)
    }

    /** No uid on the page means no call worth making - and no crash either. */
    @Test
    fun `a main page with no uid gives up quietly`() = runTest {
        signIn()
        respondWith(onlineApi, "<html>nothing useful</html>")

        assertFalse(repository().onPushTokenRefreshed("the-new-token"))
        coVerify(exactly = 0) { phpApi.register(any(), any(), any()) }
        assertReported(PushRegistrationSource.TokenRefresh, PushRegistrationOutcome.NoUid)
    }

    @Test
    fun `a throwing forum is a failed registration rather than a crash`() = runTest {
        signIn()
        coEvery { onlineApi.getMainPageHtml() } throws IllegalStateException("no network")

        assertFalse(repository().onPushTokenRefreshed("the-new-token"))
        assertReported(PushRegistrationSource.TokenRefresh, PushRegistrationOutcome.Exception)
    }

    /**
     * The other source. [NoPushTokenProvider] is what the desktop has and what firebase failing
     * looks like on Android, so a login gets as far as having nobody to register.
     */
    @Test
    fun `a login with no token to offer is reported against the login`() = runTest {
        coEvery { onlineApi.login(any()) } returns ZumpaGenericResponse(
            byteArrayOf(),
            contentType = null,
            //zumpa answers a successful login with a redirect
            status = 302,
        )

        val result = repository().login("honza", "hunter2")

        assertTrue(result.isLoggedIn)
        assertFalse(result.isPushRegistered)
        assertReported(PushRegistrationSource.Login, PushRegistrationOutcome.NoToken)
    }

    /** The schema a console query is written against, so it is worth one test of its own. */
    @Test
    fun `the event carries the source and the outcome as the values the console groups by`() {
        val event = AnalyticsEvent.PushRegistration(
            PushRegistrationSource.TokenRefresh,
            PushRegistrationOutcome.NoUid,
        )

        assertEquals("push_registration", event.name)
        assertEquals(mapOf("source" to "token_refresh", "outcome" to "no_uid"), event.params)
    }
}

private class RecordingAnalyticsReporter : AnalyticsReporter {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(property: AnalyticsUserProperty, value: String) = Unit
}
