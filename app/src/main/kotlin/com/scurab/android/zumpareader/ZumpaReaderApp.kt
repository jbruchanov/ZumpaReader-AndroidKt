package com.scurab.android.zumpareader

import android.app.Activity
import android.app.Application
import android.graphics.Point
import android.os.Bundle
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.data.PicassoHttpDownloader
import com.scurab.android.zumpareader.data.ZumpaConverterFactory
import com.scurab.android.zumpareader.model.ZumpaReadState
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.ZumpaPrefs
import com.scurab.android.zumpareader.util.execOn
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.logging.HttpLoggingInterceptor
import com.squareup.picasso.Picasso
import retrofit.Retrofit
import retrofit.RxJavaCallAdapterFactory
import java.lang.reflect.Type
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
    private var _zumpaReadStates: TreeMap<String, ZumpaReadState> = TreeMap()
    public val zumpaReadStates: TreeMap<String, ZumpaReadState> get() { return _zumpaReadStates }
    private val gson: Gson = Gson()
    private val MAX_STATES_TO_STORE = 100

    private var zumpaHttpClient : OkHttpClient? = null

    override fun onCreate() {
        super.onCreate()
        loadReadStates();

        initPicasso()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks{
            private var activities = 0
            override fun onActivityStarted(activity: Activity?) {
                activities++
            }
            override fun onActivityResumed(activity: Activity?) { }
            override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) { }
            override fun onActivityDestroyed(activity: Activity?) { }
            override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) { }
            override fun onActivityPaused(activity: Activity?) { }
            override fun onActivityStopped(activity: Activity?) {
                activities--
                if (activities == 0) {
                    storeReadStates()
                }
            }
        })
    }

    private fun loadReadStates() {
        val json = zumpaPrefs.readStates
        if (json != null) {
            _zumpaReadStates = gson.fromJson(json, object : TypeToken<TreeMap<String, ZumpaReadState>>() {}.type)
        }
    }

    private fun storeReadStates() {
        var toStore: Map<String, ZumpaReadState> = zumpaReadStates
        if (zumpaReadStates.size > MAX_STATES_TO_STORE) {
            var iterator = zumpaReadStates.descendingKeySet().iterator();
            var last = iterator.next()
            var first: String = ""
            for (i in 1..MAX_STATES_TO_STORE) {
                first = iterator.next()
            }
            toStore = zumpaReadStates.subMap(first, last)
        }
        val json = gson.toJson(toStore)
        zumpaPrefs.readStates = json
    }

    private fun initPicasso() {
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