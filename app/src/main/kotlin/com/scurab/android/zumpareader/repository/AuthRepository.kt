package com.scurab.android.zumpareader.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.ZumpaPHPAPI
import com.scurab.android.zumpareader.model.ZumpaLoginBody
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.ParseUtils
import com.scurab.android.zumpareader.util.ZumpaPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URI
import kotlin.coroutines.resume

/** What a login attempt produced - the push half can fail on its own. */
data class LoginResult(val isLoggedIn: Boolean, val isPushRegistered: Boolean)

/**
 * Login, logout and push registration - roughly 90 lines that used to sit inline in
 * `SettingsActivity`, which is why none of it could be tested.
 *
 * Also owns re-priming the parser and the cookie jar after a credential change. That used to happen
 * in the activity's `onPause`, far from the login that invalidated them.
 */
class AuthRepository(
    private val onlineApi: ZumpaAPI,
    private val phpApi: ZumpaPHPAPI,
    private val prefs: ZumpaPrefs,
    private val parser: ZumpaSimpleParser,
    private val cookieManager: CookieManager,
) {

    suspend fun login(userName: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val body = ZumpaLoginBody(userName, password)
        val response = onlineApi.login(body).execute()
        //zumpa answers a successful login with a redirect
        val isLoggedIn = response.code() == HttpURLConnection.HTTP_MOVED_TEMP

        prefs.isLoggedIn = isLoggedIn
        prefs.cookies = if (isLoggedIn) ParseUtils.extractCookies(response) else null
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
            "[OK]" == phpApi.unregister(userName).execute().body()!!.asUTFString()
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
        cookieManager.cookieStore.removeAll()
        cookieManager.put(URI.create(ZR.Constants.ZUMPA_MAIN_URL), prefs.cookiesMap)
    }

    private suspend fun registerForPush(userName: String): Boolean {
        return try {
            val token = FirebaseMessaging.getInstance().token.awaitResultOrNull()
            prefs.pushRegId = token
            if (token == null) {
                return false
            }
            val html = onlineApi.getMainPageHtml().execute().body()!!.asString()
            val uid = ZumpaSimpleParser.parseUID(html) ?: return false
            "[OK]" == phpApi.register(userName, uid, token).execute().body()!!.asUTFString()
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }
}

/** A failed task is not an error here, it only means there is no push token. */
private suspend fun <T> Task<T>.awaitResultOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        continuation.resume(if (task.isSuccessful) task.result else null)
    }
}
