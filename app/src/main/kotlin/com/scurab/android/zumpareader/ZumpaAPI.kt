package com.scurab.android.zumpareader

import com.scurab.android.zumpareader.model.*
import io.reactivex.Observable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

/**
 * Created by JBruchanov on 24/11/2015.
 */

interface ZumpaAPI {

    @GET("/phorum/list.php?f=2&a=2&af=2")
    fun getMainPage(@Query(value = "af") filter: String): Observable<ZumpaMainPageResult>

    @GET("/phorum/list.php?f=2")
    fun getMainPageHtml(): Call<ZumpaGenericResponse>

    @GET("/phorum/list.php?f=2&a=2&af=2")
    fun getMainPage(@Query(value = "t") fromThread: String, @Query(value = "af") filter: String): Observable<ZumpaMainPageResult>

    @GET("/phorum/read.php?f=2")
    fun getThreadPage(@Query(value = "i") id: String, @Query(value = "t") id2: String): Observable<ZumpaThreadResult>

    @POST("/phorum/post.php")
    fun sendResponse(@Query(value = "i") id: String, @Query(value = "t") id2: String, @Body body: ZumpaThreadBody): Observable<ZumpaThreadResult>

    @POST("/phorum/post.php")
    fun sendThread(@Body body: ZumpaThreadBody): Observable<ZumpaThreadResult>

    @POST("/login.php")
    fun login(@Body body: ZumpaLoginBody): Call<ZumpaGenericResponse>

    @POST("/phorum/rate.php")
    fun voteSurvey(@Body body: ZumpaVoteSurveyBody): Observable<ZumpaGenericResponse>

    @POST("/phorum/rate.php")
    fun toggleRate(@Body body: ZumpaToggleBody): Observable<ZumpaGenericResponse>
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
    fun postImage(@Part image: MultipartBody.Part, @Part("name") name: RequestBody) : Observable<ZumpaGenericResponse>
}

class ZumpaOfflineApi(var offlineData: LinkedHashMap<String, ZumpaThread>) : ZumpaAPI {

    override fun getMainPage(filter: String): Observable<ZumpaMainPageResult> {
        return Observable.just(ZumpaMainPageResult(null, "", offlineData))
    }

    override fun getMainPageHtml(): Call<ZumpaGenericResponse> {
        throw UnsupportedOperationException()
    }

    override fun getMainPage(fromThread: String, filter: String): Observable<ZumpaMainPageResult> {
        return getMainPage(filter)
    }

    override fun getThreadPage(id: String, id2: String): Observable<ZumpaThreadResult> {
        var data = offlineData[id]?.offlineItems ?: listOf()
        return Observable.just(ZumpaThreadResult(data))
    }

    override fun sendResponse(id: String, id2: String, body: ZumpaThreadBody): Observable<ZumpaThreadResult> {
        throw UnsupportedOperationException()
    }

    override fun sendThread(body: ZumpaThreadBody): Observable<ZumpaThreadResult> {
        throw UnsupportedOperationException()
    }

    override fun login(body: ZumpaLoginBody): Call<ZumpaGenericResponse> {
        throw UnsupportedOperationException()
    }

    override fun voteSurvey(body: ZumpaVoteSurveyBody): Observable<ZumpaGenericResponse> {
        throw UnsupportedOperationException()
    }

    override fun toggleRate(body: ZumpaToggleBody): Observable<ZumpaGenericResponse> {
        throw UnsupportedOperationException()
    }
}