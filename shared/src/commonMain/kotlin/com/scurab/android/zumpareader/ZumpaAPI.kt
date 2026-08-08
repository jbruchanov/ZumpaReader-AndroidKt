package com.scurab.android.zumpareader

import com.scurab.android.zumpareader.model.ZumpaGenericResponse
import com.scurab.android.zumpareader.model.ZumpaLoginBody
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.model.ZumpaToggleBody
import com.scurab.android.zumpareader.model.ZumpaVoteSurveyBody
import com.scurab.android.zumpareader.model.ZumpaWSBody

/**
 * The forum's endpoints. Plain suspend functions since the move to Ktor - the retrofit annotations
 * and the `Call<T>` returns are gone, and so are the five `Converter` classes that used to sit
 * behind them. The implementation is [com.scurab.android.zumpareader.data.ZumpaApiImpl]; the
 * request shapes it builds are documented there.
 *
 * Still an interface because [ZumpaOfflineApi] is the other half of the offline switch.
 */
interface ZumpaAPI {

    suspend fun getMainPage(filter: String): ZumpaMainPageResult

    suspend fun getMainPage(fromThread: String, filter: String): ZumpaMainPageResult

    /** The list page unparsed - the only thing that wants it is the uid scrape during push setup. */
    suspend fun getMainPageHtml(): ZumpaGenericResponse

    suspend fun getThreadPage(id: String, id2: String): ZumpaThreadResult

    suspend fun sendResponse(id: String, id2: String, body: ZumpaThreadBody): ZumpaThreadResult

    suspend fun sendThread(body: ZumpaThreadBody): ZumpaThreadResult

    suspend fun login(body: ZumpaLoginBody): ZumpaGenericResponse

    suspend fun voteSurvey(body: ZumpaVoteSurveyBody): ZumpaGenericResponse

    suspend fun toggleRate(body: ZumpaToggleBody): ZumpaGenericResponse
}

interface ZumpaWSAPI {
    suspend fun getZumpa(body: ZumpaWSBody): ZumpaGenericResponse
}

interface ZumpaPHPAPI {
    suspend fun register(user: String, uid: String, regId: String): ZumpaGenericResponse

    suspend fun unregister(user: String): ZumpaGenericResponse

    suspend fun postImage(fileName: String, image: ByteArray): ZumpaGenericResponse
}

class ZumpaOfflineApi(var offlineData: LinkedHashMap<String, ZumpaThread>) : ZumpaAPI {

    override suspend fun getMainPage(filter: String): ZumpaMainPageResult {
        return ZumpaMainPageResult(null, "", offlineData)
    }

    override suspend fun getMainPage(fromThread: String, filter: String): ZumpaMainPageResult {
        return getMainPage(filter)
    }

    override suspend fun getMainPageHtml(): ZumpaGenericResponse {
        throw UnsupportedOperationException()
    }

    override suspend fun getThreadPage(id: String, id2: String): ZumpaThreadResult {
        val data = offlineData[id]?.offlineItems ?: listOf()
        return ZumpaThreadResult(data)
    }

    override suspend fun sendResponse(id: String, id2: String, body: ZumpaThreadBody): ZumpaThreadResult {
        throw UnsupportedOperationException()
    }

    override suspend fun sendThread(body: ZumpaThreadBody): ZumpaThreadResult {
        throw UnsupportedOperationException()
    }

    override suspend fun login(body: ZumpaLoginBody): ZumpaGenericResponse {
        throw UnsupportedOperationException()
    }

    override suspend fun voteSurvey(body: ZumpaVoteSurveyBody): ZumpaGenericResponse {
        throw UnsupportedOperationException()
    }

    override suspend fun toggleRate(body: ZumpaToggleBody): ZumpaGenericResponse {
        throw UnsupportedOperationException()
    }
}
