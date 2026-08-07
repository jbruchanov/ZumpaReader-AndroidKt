package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.util.ZumpaPrefs
import java.net.CookieManager
import java.net.URI

/**
 * Owns what is in the cookie jar.
 *
 * `java.net.CookieManager` keeps every `Set-Cookie` the forum hands out and nothing ever throws the
 * accumulated ones away, so after a long enough session the `Cookie` header outgrows what the
 * backend accepts and it answers **502** to everything. The only cookies that matter are the ones
 * from the login response, and those are in [ZumpaPrefs.cookies].
 *
 * [reset] is therefore both the initial prime and the fix: drop the jar, rebuild it from the stored
 * login cookies. The user stays logged in - which is the whole point, since the workaround until now
 * was logging out and back in by hand.
 */
class CookieRepository(
    private val cookieManager: CookieManager,
    private val prefs: ZumpaPrefs,
) {

    fun reset() {
        cookieManager.cookieStore.removeAll()
        cookieManager.put(URI.create(ZR.Constants.ZUMPA_MAIN_URL), prefs.cookiesMap)
    }
}
