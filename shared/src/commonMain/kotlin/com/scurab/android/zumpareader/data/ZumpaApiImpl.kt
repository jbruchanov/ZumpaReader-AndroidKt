package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.ZumpaPHPAPI
import com.scurab.android.zumpareader.model.ZumpaBody
import com.scurab.android.zumpareader.model.ZumpaGenericResponse
import com.scurab.android.zumpareader.model.ZumpaLoginBody
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.model.ZumpaToggleBody
import com.scurab.android.zumpareader.model.ZumpaVoteSurveyBody
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.decodeLatin2
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers as KtorHeaders
import io.ktor.http.contentType

/**
 * The forum over Ktor. What used to be retrofit annotations plus `ZumpaConverterFactory`,
 * `ZumpaMainPageConverter`, `ZumpaThreadPageConverter`, `ZumpaGenericConverter` and
 * `ZumpaGenericConverterFactory` - five classes whose whole job was "read the bytes, decode
 * ISO-8859-2, hand them to the parser", which is one line here.
 *
 * Two things are deliberately odd and are kept from the retrofit version:
 *
 * - **`af` is sent twice** on the list endpoints. The retrofit path had `af=2` in the annotated
 *   url *and* an `@Query("af")`, so both went out and the backend took the last. Sending one would
 *   be a change in what the forum receives.
 * - **Form bodies are pre-encoded.** [ZumpaBody.toHttpPostString] already percent-encodes in
 *   ISO-8859-2, so the string is pure ascii by then and goes out as-is.
 */
class ZumpaApiImpl(
    private val client: HttpClient,
    private val parser: ZumpaSimpleParser,
    private val baseUrl: String = ZR.Constants.ZUMPA_MAIN_URL,
) : ZumpaAPI {

    override suspend fun getMainPage(filter: String): ZumpaMainPageResult =
        parser.parseMainPage(listPage(filter = filter).zumpaText())

    override suspend fun getMainPage(fromThread: String, filter: String): ZumpaMainPageResult =
        parser.parseMainPage(listPage(filter = filter, fromThread = fromThread).zumpaText())

    override suspend fun getMainPageHtml(): ZumpaGenericResponse =
        client.get("$baseUrl/phorum/list.php") {
            parameter("f", "2")
            expectSuccess = false
        }.asGenericResponse()

    override suspend fun getThreadPage(id: String, id2: String): ZumpaThreadResult =
        parser.parseThread(
            client.get("$baseUrl/phorum/read.php") {
                parameter("f", "2")
                parameter("i", id)
                parameter("t", id2)
            }.zumpaText(),
            parser.userName,
        )

    override suspend fun sendResponse(id: String, id2: String, body: ZumpaThreadBody): ZumpaThreadResult =
        parser.parseThread(
            client.post("$baseUrl/phorum/post.php") {
                parameter("i", id)
                parameter("t", id2)
                formBody(body)
            }.zumpaText(),
            parser.userName,
        )

    override suspend fun sendThread(body: ZumpaThreadBody): ZumpaThreadResult =
        parser.parseThread(
            client.post("$baseUrl/phorum/post.php") { formBody(body) }.zumpaText(),
            parser.userName,
        )

    /**
     * Reads the status instead of throwing on it: a successful login *is* a 302, and
     * [com.scurab.android.zumpareader.repository.AuthRepository] wants the `Set-Cookie`s off it.
     */
    override suspend fun login(body: ZumpaLoginBody): ZumpaGenericResponse =
        client.post("$baseUrl/login.php") {
            formBody(body)
            expectSuccess = false
        }.asGenericResponse()

    override suspend fun voteSurvey(body: ZumpaVoteSurveyBody): ZumpaGenericResponse =
        client.post("$baseUrl/phorum/rate.php") { formBody(body) }.asGenericResponse()

    override suspend fun toggleRate(body: ZumpaToggleBody): ZumpaGenericResponse =
        client.post("$baseUrl/phorum/rate.php") { formBody(body) }.asGenericResponse()

    private suspend fun listPage(filter: String, fromThread: String? = null): HttpResponse =
        client.get("$baseUrl/phorum/list.php") {
            parameter("f", "2")
            parameter("a", "2")
            parameter("af", "2")
            if (fromThread != null) {
                parameter("t", fromThread)
            }
            parameter("af", filter)
        }
}

class ZumpaPHPApiImpl(
    private val client: HttpClient,
    private val baseUrl: String = ZR.Constants.ZUMPA_PHP_MAIN_URL,
) : ZumpaPHPAPI {

    override suspend fun register(user: String, uid: String, regId: String): ZumpaGenericResponse =
        client.get("$baseUrl/CDM/RegisterHandler.php") {
            parameter("register", "true")
            parameter("platform", "android")
            parameter("user", user)
            parameter("uid", uid)
            parameter("regid", regId)
            expectSuccess = false
        }.asGenericResponse()

    override suspend fun unregister(user: String): ZumpaGenericResponse =
        client.get("$baseUrl/CDM/RegisterHandler.php") {
            parameter("unregister", "true")
            parameter("user", user)
            expectSuccess = false
        }.asGenericResponse()

    override suspend fun postImage(fileName: String, image: ByteArray): ZumpaGenericResponse =
        client.post("$baseUrl/fotodisk.php") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "image",
                            value = image,
                            headers = KtorHeaders.build {
                                append(HttpHeaders.ContentType, "image/*")
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                        append("name", "Submit")
                    },
                ),
            )
            expectSuccess = false
        }.asGenericResponse()
}

/** The forum's pages are ISO-8859-2, so the bytes are decoded rather than trusted to be text. */
private suspend fun HttpResponse.zumpaText(): String = bodyAsBytes().decodeLatin2()

private suspend fun HttpResponse.asGenericResponse(): ZumpaGenericResponse = ZumpaGenericResponse(
    data = bodyAsBytes(),
    contentType = headers[HttpHeaders.ContentType],
    status = status.value,
    setCookies = headers.getAll(HttpHeaders.SetCookie).orEmpty(),
)

/**
 * The body is already form-encoded in the forum's charset by [ZumpaBody.toHttpPostString], so it is
 * sent verbatim - re-encoding it would double-escape every diacritic.
 */
private fun io.ktor.client.request.HttpRequestBuilder.formBody(body: ZumpaBody) {
    header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
    setBody(body.toHttpPostString())
}
