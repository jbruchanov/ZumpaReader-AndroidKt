package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.model.ZumpaLoginBody
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.CookieRepository
import com.scurab.android.zumpareader.util.ZumpaPrefs
import com.scurab.android.zumpareader.util.ignoringZumpaRedirect
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the Ktor client that replaced the okhttp/retrofit stack, including the two behaviours the
 * old `OversizedCookieInterceptorTest` guarded - the 502 cookie reset and its one-shot retry - now
 * exercised through a real client rather than a mocked `Interceptor.Chain`.
 *
 * The rest is here because these are the parts of the port that a compiler cannot check: that a 302
 * is still read as success rather than followed, that the pre-encoded form body goes out verbatim,
 * that `af` is still sent twice, and that a Latin-2 page still arrives as Czech text.
 */
class ZumpaHttpClientTest {

    private val prefs = mockk<ZumpaPrefs>(relaxed = true).also {
        every { it.cookies } returns setOf("PHPSESSID=abc123; path=/")
        every { it.showLastAuthor } returns false
    }
    private val cookies = CookieRepository(prefs)
    private val parser = ZumpaSimpleParser()

    private fun fixtureBytes(name: String): ByteArray =
        requireNotNull(javaClass.classLoader.getResourceAsStream(name)).use { it.readBytes() }

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<HttpClient, MockEngine> {
        val engine = MockEngine { request -> handler(request) }
        return buildZumpaHttpClient(engine, cookies, isDebug = false) to engine
    }

    private suspend fun HttpRequestData.bodyText(): String =
        body.let { content ->
            when (content) {
                is io.ktor.http.content.TextContent -> content.text
                is io.ktor.http.content.ByteArrayContent -> content.bytes().decodeToString()
                else -> content.toString()
            }
        }

    //region oversized cookie retry
    @Test
    fun `a successful response is passed through untouched`() = runBlocking {
        val (http, engine) = client { respond("ok") }

        val api = ZumpaPHPApiImpl(http, baseUrl = "https://php.test")
        assertEquals("ok", api.unregister("someone").asUTFString())
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `a 502 resets the cookies and sends the request again`() = runBlocking {
        var call = 0
        val (http, engine) = client {
            call++
            if (call == 1) respondError(HttpStatusCode.BadGateway) else respond("ok")
        }

        val api = ZumpaPHPApiImpl(http, baseUrl = "https://php.test")
        assertEquals("ok", api.unregister("someone").asUTFString())
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun `a second 502 is returned rather than retried forever`() = runBlocking {
        val (http, engine) = client { respondError(HttpStatusCode.BadGateway) }

        val api = ZumpaPHPApiImpl(http, baseUrl = "https://php.test")
        assertEquals(HttpStatusCode.BadGateway.value, api.unregister("someone").status)
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun `other server errors are not treated as an oversized cookie`() = runBlocking {
        val (http, engine) = client { respondError(HttpStatusCode.InternalServerError) }

        val api = ZumpaPHPApiImpl(http, baseUrl = "https://php.test")
        assertEquals(HttpStatusCode.InternalServerError.value, api.unregister("someone").status)
        assertEquals(1, engine.requestHistory.size)
    }
    //endregion

    //region redirects
    @Test
    fun `a 302 is not followed and counts as a successful post`() = runBlocking {
        val (http, engine) = client {
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/somewhere/else"))
        }

        val api = ZumpaApiImpl(http, parser, baseUrl = "https://zumpa.test")
        val posted = ignoringZumpaRedirect { api.sendThread(ZumpaThreadBody("a", "s", "b")) }

        assertTrue(posted)
        //one request only - following the redirect would show up as a second
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `a real failure is not swallowed by the redirect handling`() = runBlocking {
        val (http, _) = client { respondError(HttpStatusCode.InternalServerError) }

        val api = ZumpaApiImpl(http, parser, baseUrl = "https://zumpa.test")
        assertThrows(io.ktor.client.plugins.ServerResponseException::class.java) {
            kotlinx.coroutines.runBlocking {
                ignoringZumpaRedirect { api.sendThread(ZumpaThreadBody("a", "s", "b")) }
            }
        }
    }

    @Test
    fun `login reads the 302 and its cookies instead of throwing`() = runBlocking {
        val (http, _) = client {
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.SetCookie, listOf("PHPSESSID=new; path=/", "extra=1")),
            )
        }

        val api = ZumpaApiImpl(http, parser, baseUrl = "https://zumpa.test")
        val response = api.login(ZumpaLoginBody("nick", "pass"))

        assertEquals(HttpStatusCode.Found.value, response.status)
        assertEquals(2, response.setCookies.size)
        assertTrue(response.setCookies.any { it.startsWith("PHPSESSID=new") })
    }
    //endregion

    //region request shape
    @Test
    fun `the list request still sends af twice`() = runBlocking {
        val (http, engine) = client { respond(fixtureBytes("mainpage_default.html")) }

        ZumpaApiImpl(http, parser, baseUrl = "https://zumpa.test").getMainPage("7")

        //`af=2` came from the annotated url and `af=<filter>` from the @Query - retrofit sent both
        assertEquals(listOf("2", "7"), engine.requestHistory.single().url.parameters.getAll("af"))
    }

    @Test
    fun `the paging request carries the thread it starts from`() = runBlocking {
        val (http, engine) = client { respond(fixtureBytes("mainpage_default.html")) }

        ZumpaApiImpl(http, parser, baseUrl = "https://zumpa.test").getMainPage("2878944", "7")

        val params = engine.requestHistory.single().url.parameters
        assertEquals("2878944", params["t"])
        assertEquals("2", params["f"])
        assertEquals("2", params["a"])
    }

    @Test
    fun `every request carries a cache busting timestamp`() = runBlocking {
        val (http, engine) = client { respond("ok") }

        ZumpaPHPApiImpl(http, baseUrl = "https://php.test").unregister("someone")

        val ts = engine.requestHistory.single().url.parameters["_ts"]
        assertNotNull(ts)
        assertTrue(requireNotNull(ts).toLong() > 0)
    }

    @Test
    fun `a form body goes out already encoded and is not escaped again`() = runBlocking {
        val (http, engine) = client { respond("", HttpStatusCode.Found) }

        val api = ZumpaApiImpl(http, parser, baseUrl = "https://zumpa.test")
        //the subject holds a character latin-2 can carry and one it cannot
        ignoringZumpaRedirect { api.sendThread(ZumpaThreadBody("me", "ěšč 😀", "body")) }

        val sent = engine.requestHistory.single().bodyText()
        //%EC is latin-2 for 'ě', and the emoji became a numeric character reference before encoding
        assertTrue(sent.contains("%EC"), "expected latin-2 percent escapes in: $sent")
        assertTrue(sent.contains("%26%23128512%3B"), "expected an escaped &#128512; in: $sent")
        assertTrue(sent.contains("author=me"))
    }
    //endregion

    //region charset
    @Test
    fun `a latin-2 page arrives as czech text and not as mojibake`() = runBlocking {
        val (http, _) = client { respond(fixtureBytes("mainpage_default.html")) }

        val result = ZumpaApiImpl(http, parser, baseUrl = "https://zumpa.test").getMainPage("2")

        assertEquals(35, result.items.size)
        assertEquals("Dobré ráno", result.items["2879193"]?.subject)
    }
    //endregion

    //region cookie jar
    @Test
    fun `the jar is primed from the stored login cookies`() = runBlocking {
        val storage = ZumpaCookiesStorage(prefs)
        storage.reset()

        val stored = storage.get(io.ktor.http.Url("https://zunpa.cz"))
        assertEquals(1, stored.size)
        assertEquals("PHPSESSID", stored.single().name)
        assertEquals("abc123", stored.single().value)
    }

    @Test
    fun `the last author cookie is added only when the setting is on`() = runBlocking {
        every { prefs.showLastAuthor } returns true
        val storage = ZumpaCookiesStorage(prefs)
        storage.reset()

        val names = storage.get(io.ktor.http.Url("https://zunpa.cz")).map { it.name }
        assertTrue(names.contains("newdate"), "expected newdate in $names")
    }

    @Test
    fun `a reset throws away everything the session accumulated`() = runBlocking {
        val storage = ZumpaCookiesStorage(prefs)
        val url = io.ktor.http.Url("https://zunpa.cz")
        storage.reset()
        storage.addCookie(url, io.ktor.http.parseServerSetCookieHeader("junk=1"))
        assertEquals(2, storage.get(url).size)

        storage.reset()

        assertEquals(listOf("PHPSESSID"), storage.get(url).map { it.name })
    }

    @Test
    fun `a cookie set twice keeps only the newer value`() = runBlocking {
        val storage = ZumpaCookiesStorage(prefs)
        val url = io.ktor.http.Url("https://zunpa.cz")
        storage.reset()

        storage.addCookie(url, io.ktor.http.parseServerSetCookieHeader("PHPSESSID=second"))

        assertEquals("second", storage.get(url).single { it.name == "PHPSESSID" }.value)
    }

    @Test
    fun `an unparseable stored cookie is skipped rather than fatal`() = runBlocking {
        every { prefs.cookies } returns setOf("", "PHPSESSID=fine")
        val storage = ZumpaCookiesStorage(prefs)
        storage.reset()

        val names = storage.get(io.ktor.http.Url("https://zunpa.cz")).map { it.name }
        assertTrue(names.contains("PHPSESSID"), "expected PHPSESSID in $names")
        assertNull(names.firstOrNull { it.isEmpty() })
    }
    //endregion
}
