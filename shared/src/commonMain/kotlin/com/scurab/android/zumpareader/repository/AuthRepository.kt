package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.ZumpaPHPAPI
import com.scurab.android.zumpareader.model.ZumpaLoginBody
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.ZumpaPrefs
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a login attempt produced - the push half can fail on its own. */
data class LoginResult(val isLoggedIn: Boolean, val isPushRegistered: Boolean)

/**
 * Login, logout and push registration - roughly 90 lines that used to sit inline in
 * the settings screen, which is why none of it could be tested.
 *
 * Also owns re-priming the parser and the cookie jar after a credential change. That used to happen
 * in the screen's `onPause`, far from the login that invalidated them.
 */
class AuthRepository(
    private val onlineApi: ZumpaAPI,
    private val phpApi: ZumpaPHPAPI,
    private val prefs: ZumpaPrefs,
    private val parser: ZumpaSimpleParser,
    private val cookies: CookieRepository,
    /** Firebase on Android - see [PushTokenProvider], which is what keeps this file platform-free. */
    private val pushTokens: PushTokenProvider,
    /** Firebase on Android too, and nothing at all on the desktop - see [NoAnalyticsReporter]. */
    private val analytics: AnalyticsReporter,
) {

    suspend fun login(userName: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val body = ZumpaLoginBody(userName, password)
        val response = onlineApi.login(body)
        //zumpa answers a successful login with a redirect
        val isLoggedIn = response.status == HttpStatusCode.Found.value

        prefs.isLoggedIn = isLoggedIn
        prefs.cookies = if (isLoggedIn) response.setCookies.toSet() else null
        if (!isLoggedIn) {
            return@withContext LoginResult(isLoggedIn = false, isPushRegistered = false)
        }

        applyCredentials()
        LoginResult(isLoggedIn = true, isPushRegistered = registerForPush(userName))
    }

    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        val userName = prefs.loggedUserName
        prefs.isLoggedIn = false
        prefs.cookies = null
        applyCredentials()

        if (userName == null) {
            return@withContext true
        }
        try {
            "[OK]" == phpApi.unregister(userName).asUTFString()
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * The parser needs the user name to spot your own posts and the cookie jar needs rebuilding
     * from the stored cookies - both are invalid the moment the credentials change.
     */
    fun applyCredentials() {
        parser.userName = prefs.loggedUserName
        parser.isShowLastUser = prefs.showLastAuthor
        cookies.reset()
    }

    /**
     * A token the platform handed over rather than one we went and asked for - firebase refreshing
     * it, which arrives at the messaging service.
     *
     * Public for that one caller. It used to have its own copy of the registration below, which is
     * how it came to read the *switching* api rather than the online one - so a token refreshed
     * while offline mode was on looked for a uid in the offline snapshot, never found one, and gave
     * up without telling anybody. It also never stored the new token.
     */
    suspend fun onPushTokenRefreshed(token: String): Boolean = withContext(Dispatchers.IO) {
        val source = PushRegistrationSource.TokenRefresh
        val userName = prefs.loggedUserName
            ?: return@withContext report(source, PushRegistrationOutcome.NoUser)
        prefs.pushRegId = token
        registerToken(userName, token, source)
    }

    private suspend fun registerForPush(userName: String): Boolean {
        val source = PushRegistrationSource.Login
        val token = try {
            pushTokens.token()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
        prefs.pushRegId = token
        return if (token == null) {
            report(source, PushRegistrationOutcome.NoToken)
        } else {
            registerToken(userName, token, source)
        }
    }

    /**
     * The forum wants the uid off the main page alongside the token, which is why this needs the
     * session and not just the token. [onlineApi] explicitly - the offline snapshot has no uid in
     * it, and registering for push is not something offline mode should be trying to do at all.
     */
    private suspend fun registerToken(
        userName: String,
        token: String,
        source: PushRegistrationSource,
    ): Boolean {
        val outcome = try {
            val html = onlineApi.getMainPageHtml().asString()
            val uid = ZumpaSimpleParser.parseUID(html)
            when {
                uid == null -> PushRegistrationOutcome.NoUid
                "[OK]" == phpApi.register(userName, uid, token).asUTFString() ->
                    PushRegistrationOutcome.Ok

                else -> PushRegistrationOutcome.Rejected
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            PushRegistrationOutcome.Exception
        }
        return report(source, outcome)
    }

    /**
     * Every exit of a registration goes through here, so the boolean the caller reads and the
     * outcome the console counts are one decision rather than two that can come apart.
     *
     * Reported at all because none of this shows: the app carries on exactly as it would have, and
     * a forum with nothing to say is indistinguishable from one whose pushes are going nowhere.
     * See [AnalyticsEvent.PushRegistration].
     */
    private fun report(source: PushRegistrationSource, outcome: PushRegistrationOutcome): Boolean {
        analytics.log(AnalyticsEvent.PushRegistration(source, outcome))
        return outcome == PushRegistrationOutcome.Ok
    }
}

//awaitResultOrNull moved with the Firebase call it wrapped - see FirebasePushTokenProvider in :app
