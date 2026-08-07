package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.repository.CookieRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OversizedCookieInterceptorTest {

    private val cookies = mockk<CookieRepository>(relaxed = true)
    private val interceptor = OversizedCookieInterceptor(cookies)
    private val request = Request.Builder().url("https://www.zumpa.cz/").build()

    private fun response(code: Int) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body("".toResponseBody())
        .build()

    /** `proceed` answers with [codes] in order, so the second call can differ from the first. */
    private fun chain(vararg codes: Int): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returnsMany codes.map { response(it) }
        return chain
    }

    @Test
    fun `a successful response is passed through untouched`() {
        val chain = chain(200)

        assertEquals(200, interceptor.intercept(chain).code)

        verify(exactly = 1) { chain.proceed(any()) }
        verify(exactly = 0) { cookies.reset() }
    }

    @Test
    fun `a 502 resets the cookies and sends the request again`() {
        val chain = chain(502, 200)

        assertEquals(200, interceptor.intercept(chain).code)

        verify(exactly = 1) { cookies.reset() }
        verify(exactly = 2) { chain.proceed(any()) }
    }

    @Test
    fun `a second 502 is returned rather than retried forever`() {
        val chain = chain(502, 502)

        assertEquals(502, interceptor.intercept(chain).code)

        verify(exactly = 1) { cookies.reset() }
        verify(exactly = 2) { chain.proceed(any()) }
    }

    @Test
    fun `other server errors are not treated as an oversized cookie`() {
        val chain = chain(500)

        assertEquals(500, interceptor.intercept(chain).code)

        verify(exactly = 0) { cookies.reset() }
    }
}
