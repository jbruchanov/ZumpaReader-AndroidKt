package com.scurab.android.zumpareader

import android.app.Application
import android.graphics.Point
import android.os.Environment
import android.util.Log
import com.scurab.android.zumpareader.data.PicassoHttpDownloader
import com.scurab.android.zumpareader.data.ZumpaConverterFactory
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.ZumpaPrefs
import com.scurab.android.zumpareader.util.execOn
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.logging.HttpLoggingInterceptor
import com.squareup.picasso.Picasso
import retrofit.Retrofit
import retrofit.RxJavaCallAdapterFactory
import java.net.CookieManager
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class ZumpaReaderApp:Application(){

    public val zumpaParser: ZumpaSimpleParser by lazy { ZumpaSimpleParser().apply { userName = zumpaPrefs.loggedUserName } }
    public val zumpaPrefs: ZumpaPrefs by lazy { ZumpaPrefs(this) }
    public val zumpaData: TreeMap<String, ZumpaThread> = TreeMap()

    private var zumpaHttpClient : OkHttpClient? = null

    override fun onCreate() {
        super.onCreate()


        val client = OkHttpClient()
        client.setConnectTimeout(2000L, TimeUnit.MILLISECONDS)
        client.setReadTimeout(2000L, TimeUnit.MILLISECONDS)
        client.setWriteTimeout(2000L, TimeUnit.MILLISECONDS)

        val picasso = Picasso.Builder(this)
                .downloader(
                        PicassoHttpDownloader(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                                Point(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels),
                                client))
                .listener({ picasso, uri, exception ->
                    Log.d("PicassoLoader", "URL:%s Exception:%s".format(uri, exception))
                    exception.printStackTrace()
                }).build()
        Picasso.setSingletonInstance(picasso)
    }

    public val zumpaAPI: ZumpaAPI by lazy {

        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL)
        cookieManager.put(URI.create(ZR.Constants.ZUMPA_MAIN_URL), zumpaPrefs.cookiesMap)

        var logging = HttpLoggingInterceptor();
        // set your desired log level
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        zumpaHttpClient = OkHttpClient()
        zumpaHttpClient.execOn {
            followRedirects = true//false for logging
            setConnectTimeout(2000L, TimeUnit.MILLISECONDS)
            setReadTimeout(2000L, TimeUnit.MILLISECONDS)
            setWriteTimeout(2000L, TimeUnit.MILLISECONDS)
            setCookieHandler(cookieManager)
            if (BuildConfig.VERBOSE_LOGGING) {
                interceptors().add(logging)
            }
        }

        val retrofit = Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
                .addConverterFactory(ZumpaConverterFactory(zumpaParser))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .client(zumpaHttpClient)
                .build()

        retrofit.create(ZumpaAPI::class.java)
    }

    public fun resetCookies() {
        zumpaHttpClient.execOn {
            setCookieHandler(CookieManager())
        }
    }

    public var followRedirects: Boolean
        get() = zumpaHttpClient?.followRedirects ?: false
        set(value) {
            zumpaHttpClient?.followRedirects = value
        }

}