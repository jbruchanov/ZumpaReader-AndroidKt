package com.scurab.android.zumpareader.util

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

/**
 * Created by JBruchanov on 27/12/2017.
 *
 * Zumpa answers a successful post with a redirect, the http client doesn't follow it,
 * so the 302 has to be taken as a success.
 */
suspend fun <T> ignoringZumpaRedirect(block: suspend () -> T): Boolean {
    return try {
        block()
        true
    } catch (e: ResponseException) {
        if (e.response.status == HttpStatusCode.Found) {
            true
        } else {
            throw e
        }
    }
}

/**
 * Retries a failing call, [retries] attempts on top of the first one.
 */
suspend fun <T> retrying(retries: Int = 3, block: suspend () -> T): T {
    var lastError: Throwable? = null
    repeat(retries + 1) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            lastError = e
        }
    }
    throw lastError!!
}
