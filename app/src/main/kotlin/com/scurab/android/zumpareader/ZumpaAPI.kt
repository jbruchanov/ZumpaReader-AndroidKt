package com.scurab.android.zumpareader

import com.scurab.android.zumpareader.model.*
import retrofit.Call
import retrofit.http.*
import rx.Observable

/**
 * Created by JBruchanov on 24/11/2015.
 */

public interface ZumpaAPI {

    @GET("/phorum/list.php?f=2")
    fun getMainPage(): Observable<ZumpaMainPageResult>;

    @GET("/phorum/list.php?f=2&a=2")
    fun getMainPage(@Query(value = "t") fromThread: String): Observable<ZumpaMainPageResult>;


    @GET("/phorum/read.php?f=2")
    fun getThreadPage(@Query(value = "i") id: String, @Query(value = "t") id2: String): Observable<ZumpaThreadResult>

    @POST("/phorum/post.php")
    fun sendResponse(@Query(value = "i") id: String, @Query(value = "t") id2: String, @Body body: ZumpaThreadBody): Call<ZumpaThreadResult>

    @POST("/phorum/post.php")
    fun sendThread(@Body body: ZumpaThreadBody): Call<ZumpaThreadResult>

    @Headers("User-Agent: Mozilla/5.0 (Linux; U; Android 2.2; en-us; Nexus One Build/FRF91) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1")
    @POST("/login.php")
    fun login(@Body body: ZumpaLoginBody): Call<ZumpaResponse>
}