package com.scurab.android.zumpareader

import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import retrofit.Call
import retrofit.http.GET
import retrofit.http.Query
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
}