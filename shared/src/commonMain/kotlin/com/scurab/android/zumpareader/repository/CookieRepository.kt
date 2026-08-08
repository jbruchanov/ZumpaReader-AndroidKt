package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.data.ZumpaCookiesStorage
import com.scurab.android.zumpareader.util.ZumpaPrefs

/**
 * Owns what is in the cookie jar.
 *
 * The jar keeps every `Set-Cookie` the forum hands out and nothing ever throws the accumulated ones
 * away, so after a long enough session the `Cookie` header outgrows what the backend accepts and it
 * answers **502** to everything. The only cookies that matter are the ones from the login response,
 * and those are in [ZumpaPrefs.cookies].
 *
 * [reset] is therefore both the initial prime and the fix: drop the jar, rebuild it from the stored
 * login cookies. The user stays logged in - which is the whole point, since the workaround until now
 * was logging out and back in by hand.
 *
 * The jar itself is a Ktor [ZumpaCookiesStorage] now rather than a `java.net.CookieManager`; this
 * stays the seam the rest of the app resets through.
 */
class CookieRepository(prefs: ZumpaPrefs) {

    /** Handed to the client's `HttpCookies` plugin - see `buildZumpaHttpClient`. */
    val storage = ZumpaCookiesStorage(prefs)

    fun reset() = storage.reset()
}
