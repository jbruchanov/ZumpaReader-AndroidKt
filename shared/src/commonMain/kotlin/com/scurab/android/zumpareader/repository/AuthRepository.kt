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

    private suspend fun registerForPush(userName: String): Boolean {
        return try {
            val token = pushTokens.token()
            prefs.pushRegId = token
            if (token == null) {
                return false
            }
            val html = onlineApi.getMainPageHtml().asString()
            val uid = ZumpaSimpleParser.parseUID(html) ?: return false
            "[OK]" == phpApi.register(userName, uid, token).asUTFString()
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }
}

//awaitResultOrNull moved with the Firebase call it wrapped - see FirebasePushTokenProvider in :app
