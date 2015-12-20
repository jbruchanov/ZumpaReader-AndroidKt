package com.scurab.android.zumpareader

import android.app.Application
import android.util.Log
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.retrofit.ZumpaConverterFactory
import com.squareup.okhttp.OkHttpClient
import com.squareup.picasso.OkHttpDownloader
import com.squareup.picasso.Picasso
import retrofit.Retrofit
import retrofit.RxJavaCallAdapterFactory
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class ZumpaReaderApp:Application(){

    override fun onCreate() {
        super.onCreate()


        val client = OkHttpClient()
        client.setConnectTimeout(2000L, TimeUnit.MILLISECONDS)
        client.setReadTimeout(2000L, TimeUnit.MILLISECONDS)
        client.setWriteTimeout(2000L, TimeUnit.MILLISECONDS)

        val picasso = Picasso.Builder(this)
                .downloader(OkHttpDownloader(client))
                .listener({ picasso, uri, exception ->
                    Log.d("PicassoLoader", "URL:%s Exception:%s".format(uri, exception))
                    exception.printStackTrace()
                }).build()
        Picasso.setSingletonInstance(picasso)
    }

    public val zumpaParser : ZumpaSimpleParser by lazy { ZumpaSimpleParser() }

    public val zumpaAPI: ZumpaAPI by lazy {
        val retrofit = Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
                .addConverterFactory(ZumpaConverterFactory(zumpaParser))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build()

        retrofit.create(ZumpaAPI::class.java)
    }

    public val zumpaData: TreeMap<String, ZumpaThread> = TreeMap()
}