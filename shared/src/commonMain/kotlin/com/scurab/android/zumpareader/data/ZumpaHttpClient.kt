package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.repository.CookieRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock

private const val TIMEOUT_MS = 5_000L
private const val BODY_TIMEOUT_MS = TIMEOUT_MS * 5

/**
 * The one http client. Replaces the hand-built `OkHttpClient` plus three `Retrofit` instances;
 * okhttp is still underneath as the engine, but nothing above this file knows that, which is the
 * point - on ios the engine becomes Darwin and everything else stays put.
 *
 * Two settings here are load-bearing rather than preference:
 *
 * - **`followRedirects = false`.** The forum answers a successful post or login with a 302, so the
 *   redirect *is* the result. Following it would turn every successful post into a mystery.
 * - **`expectSuccess = true`.** Retrofit threw on a non-2xx and the code above depends on that;
 *   [com.scurab.android.zumpareader.util.ignoringZumpaRedirect] is what turns the resulting 302
 *   back into a success. The endpoints that want to read a status instead of catching it pass
 *   `expectSuccess = false` per request.
 */
fun buildZumpaHttpClient(
    engine: HttpClientEngine,
    cookies: CookieRepository,
    isDebug: Boolean,
): HttpClient {
    cookies.reset()

    val client = HttpClient(engine) {
        followRedirects = false
        expectSuccess = true

        install(HttpTimeout) {
            connectTimeoutMillis = TIMEOUT_MS
            requestTimeoutMillis = BODY_TIMEOUT_MS
            socketTimeoutMillis = BODY_TIMEOUT_MS
        }
        install(HttpCookies) { storage = cookies.storage }
        install(CacheBustingPlugin)
        if (isDebug) {
            install(Logging) { level = LogLevel.ALL }
        }
        defaultRequest {
            header("Cache-Control", "max-age=0")
        }
    }

    client.installOversizedCookieRetry(cookies)
    return client
}

/**
 * The forum caches aggressively, so every request carries a throwaway `_ts`. Was a network
 * interceptor on the okhttp client.
 */
private val CacheBustingPlugin = createClientPlugin("ZumpaCacheBusting") {
    onRequest { request, _ ->
        request.url.parameters.append("_ts", Clock.System.now().toEpochMilliseconds().toString())
    }
}

/**
 * The forum answers **502** once the `Cookie` header has grown past what it will accept. Throw the
 * accumulated jar away, rebuild it from the login cookies and send the request again - see
 * [CookieRepository].
 *
 * [HttpSend] and not a plain plugin: the cookie header is rendered further down the pipeline, so a
 * second `execute` of the same request picks up the reset jar. This is the same reason the okhttp
 * version had to be an *application* interceptor rather than a network one.
 *
 * One retry, never a loop: a 502 that is a genuinely broken gateway comes straight back out.
 */
fun HttpClient.installOversizedCookieRetry(cookies: CookieRepository) {
    plugin(HttpSend).intercept { request ->
        val call = execute(request)
        if (call.response.status != HttpStatusCode.BadGateway) {
            return@intercept call
        }
        cookies.reset()
        execute(request)
    }
}
