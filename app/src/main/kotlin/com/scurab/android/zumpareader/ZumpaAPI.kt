package com.scurab.android.zumpareader

import com.scurab.android.zumpareader.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

/**
 * Created by JBruchanov on 24/11/2015.
 */

interface ZumpaAPI {

    @GET("/phorum/list.php?f=2&a=2&af=2")
    suspend fun getMainPage(@Query(value = "af") filter: String): ZumpaMainPageResult

    @GET("/phorum/list.php?f=2")
    fun getMainPageHtml(): Call<ZumpaGenericResponse>

    @GET("/phorum/list.php?f=2&a=2&af=2")
    suspend fun getMainPage(@Query(value = "t") fromThread: String, @Query(value = "af") filter: String): ZumpaMainPageResult

    @GET("/phorum/read.php?f=2")
    suspend fun getThreadPage(@Query(value = "i") id: String, @Query(value = "t") id2: String): ZumpaThreadResult

    @POST("/phorum/post.php")
    suspend fun sendResponse(@Query(value = "i") id: String, @Query(value = "t") id2: String, @Body body: ZumpaThreadBody): ZumpaThreadResult

    @POST("/phorum/post.php")
    suspend fun sendThread(@Body body: ZumpaThreadBody): ZumpaThreadResult

    @POST("/login.php")
    fun login(@Body body: ZumpaLoginBody): Call<ZumpaGenericResponse>

    @POST("/phorum/rate.php")
    suspend fun voteSurvey(@Body body: ZumpaVoteSurveyBody): ZumpaGenericResponse

    @POST("/phorum/rate.php")
    suspend fun toggleRate(@Body body: ZumpaToggleBody): ZumpaGenericResponse
}

interface ZumpaWSAPI {
    @POST("/zumpa")
    fun getZumpa(@Body body: ZumpaWSBody): Call<ZumpaGenericResponse>
}

interface ZumpaPHPAPI {
    @GET("/CDM/RegisterHandler.php?register=true&platform=android")
    fun register(@Query("user") user: String, @Query("uid") uid: String, @Query("regid") regId: String): Call<ZumpaGenericResponse>

    @GET("/CDM/RegisterHandler.php?unregister=true")
    fun unregister(@Query("user") user: String): Call<ZumpaGenericResponse>

    @Multipart()
    @POST("/fotodisk.php")
    suspend fun postImage(@Part image: MultipartBody.Part, @Part("name") name: RequestBody): ZumpaGenericResponse
}

class ZumpaOfflineApi(var offlineData: LinkedHashMap<String, ZumpaThread>) : ZumpaAPI {

    override suspend fun getMainPage(filter: String): ZumpaMainPageResult {
        return ZumpaMainPageResult(null, "", offlineData)
    }

    override fun getMainPageHtml(): Call<ZumpaGenericResponse> {
        throw UnsupportedOperationException()
    }

    override suspend fun getMainPage(fromThread: String, filter: String): ZumpaMainPageResult {
        return getMainPage(filter)
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

    override fun login(body: ZumpaLoginBody): Call<ZumpaGenericResponse> {
        throw UnsupportedOperationException()
    }

    override suspend fun voteSurvey(body: ZumpaVoteSurveyBody): ZumpaGenericResponse {
        throw UnsupportedOperationException()
    }

    override suspend fun toggleRate(body: ZumpaToggleBody): ZumpaGenericResponse {
        throw UnsupportedOperationException()
    }
}