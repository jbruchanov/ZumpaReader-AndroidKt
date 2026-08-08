package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.util.ZumpaPrefs
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader

/**
 * Accepts every cookie the forum hands out and keeps them for the session, which is what
 * `java.net.CookieManager` with `ACCEPT_ALL` behind okhttp's `JavaNetCookieJar` used to do.
 *
 * [reset] is the part that matters - see [com.scurab.android.zumpareader.repository.CookieRepository]
 * for why throwing the jar away is both the initial prime and the fix for the 502s.
 *
 * Copy-on-write rather than locked: the operations are a list swap, `get` never needs to see a
 * half-applied update, and a lost race on two simultaneous `Set-Cookie`s costs a cookie that the
 * next response sets again. A `Mutex` would also force [reset] to become `suspend`, and it is called
 * from `applyCredentials`, which is not.
 */
class ZumpaCookiesStorage(private val prefs: ZumpaPrefs) : CookiesStorage {

    @Volatile
    private var cookies: List<Cookie> = emptyList()

    override suspend fun get(requestUrl: Url): List<Cookie> = cookies

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        //last one wins per name, as a cookie jar does
        cookies = cookies.filterNot { it.name == cookie.name } + cookie
    }

    /** Drops everything accumulated and rebuilds the jar from the stored login cookies. */
    fun reset() {
        cookies = storedCookies()
    }

    private fun storedCookies(): List<Cookie> {
        val raw = prefs.cookies.orEmpty().toMutableList()
        if (prefs.showLastAuthor) {
            raw += "${ZR.Constants.ZUMPA_SHOW_LAST_ANSWER_AUTHOR_KEY}=1;"
        }
        return raw.mapNotNull { header ->
            runCatching { parseServerSetCookieHeader(header) }.getOrNull()
        }
    }

    override fun close() = Unit
}
