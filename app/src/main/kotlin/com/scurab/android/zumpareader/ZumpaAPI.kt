package com.scurab.android.zumpareader

import com.scurab.android.zumpareader.model.*
import retrofit.Call
import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Query
import rx.Observable

/**
 * Created by JBruchanov on 24/11/2015.
 */

public interface ZumpaAPI {

    @GET("/phorum/list.php?f=2&a=2&af=2")
    fun getMainPage(@Query(value = "af") filter: String): Observable<ZumpaMainPageResult>;

    @GET("/phorum/list.php?f=2")
    fun getMainPageHtml(): Call<ZumpaGenericResponse>;

    @GET("/phorum/list.php?f=2&a=2&af=2")
    fun getMainPage(@Query(value = "t") fromThread: String, @Query(value = "af") filter: String): Observable<ZumpaMainPageResult>;

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
}

public interface ZumpaWSAPI {
    @POST("/zumpa")
    fun getZumpa(@Body body: ZumpaWSBody): Call<ZumpaGenericResponse>
}

public interface ZumpaPHPAPI {
    @GET("/CDM/RegisterHandler.php?register=true&platform=android")
    fun register(@Query("user") user: String, @Query("uid") uid: String, @Query("regid") regId: String): Call<ZumpaGenericResponse>

    @GET("/CDM/RegisterHandler.php?unregister=true")
    fun unregister(@Query("user") user: String): Call<ZumpaGenericResponse>
}