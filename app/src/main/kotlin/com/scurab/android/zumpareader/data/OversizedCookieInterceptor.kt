package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.repository.CookieRepository
import java.net.HttpURLConnection
import okhttp3.Interceptor
import okhttp3.Response

/**
 * The forum answers **502** once the `Cookie` header has grown past what it will accept. Throw the
 * accumulated jar away, rebuild it from the login cookies and send the request again - see
 * [CookieRepository].
 *
 * Has to be an *application* interceptor: okhttp's `BridgeInterceptor` is what writes the `Cookie`
 * header, and it runs below these, so the second [Interceptor.Chain.proceed] picks up the reset jar.
 * A network interceptor would re-send the oversized header it was given.
 *
 * One retry, never a loop: a 502 that is a genuinely broken gateway comes straight back out.
 */
class OversizedCookieInterceptor(private val cookies: CookieRepository) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != HttpURLConnection.HTTP_BAD_GATEWAY) {
            return response
        }
        response.close()
        cookies.reset()
        return chain.proceed(chain.request())
    }
}
