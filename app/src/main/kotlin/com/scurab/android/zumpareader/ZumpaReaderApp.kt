package com.scurab.android.zumpareader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Environment
import android.util.Log
import com.github.salomonbrys.kotson.simpleDeserialize
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.scurab.android.zumpareader.data.PicassoHttpDownloader
import com.scurab.android.zumpareader.data.ZumpaConverterFactory
import com.scurab.android.zumpareader.data.ZumpaGenericConverterFactory
import com.scurab.android.zumpareader.gson.GsonExcludeStrategy
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
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.CookieManager
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class ZumpaReaderApp:Application(){

    companion object {
        val OFFLINE_FILE_NAME = "offline.json"
    }

    public val zumpaParser: ZumpaSimpleParser by lazy {
        val v = ZumpaSimpleParser()
        v.userName = zumpaPrefs.loggedUserName
        v.isShowLastUser = zumpaPrefs.showLastAuthor
        v
    }

    public val zumpaPrefs: ZumpaPrefs by lazy { ZumpaPrefs(this) }
    public val zumpaData: TreeMap<String, ZumpaThread> = TreeMap()
    private var _zumpaReadStates: TreeMap<String, ZumpaReadState> = TreeMap()
    public val zumpaReadStates: TreeMap<String, ZumpaReadState> get() { return _zumpaReadStates }
    public val cookieManager: CookieManager = CookieManager()
    private val gson: Gson = Gson()
    private val MAX_STATES_TO_STORE = 100
    private val TIMEOUT = 5000L

    public val zumpaHttpClient by lazy {
        cookieManager.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL)
        cookieManager.put(URI.create(ZR.Constants.ZUMPA_MAIN_URL), zumpaPrefs.cookiesMap)

        var logging = HttpLoggingInterceptor();
        // set your desired log level
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        val zumpaHttpClient = OkHttpClient()
        zumpaHttpClient.execOn {
            this.followRedirects = true//false for logging
            setConnectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
            setReadTimeout(TIMEOUT * 5, TimeUnit.MILLISECONDS)
            setWriteTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
            setCookieHandler(cookieManager)
            if (BuildConfig.VERBOSE_LOGGING) {
                interceptors().add(logging)
            }
        }
        zumpaHttpClient
    }

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

        val offline = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), OFFLINE_FILE_NAME)
        if (offline.exists() && zumpaPrefs.isOffline) {
            val gsonBuilder = GsonBuilder().setExclusionStrategies(GsonExcludeStrategy())
            gsonBuilder.simpleDeserialize { elem -> ZumpaThread.thread(elem as JsonObject) }
            val gson = gsonBuilder.create()
            val type = object : TypeToken<LinkedHashMap<String, ZumpaThread>>() {}.type
            val jsonReader = JsonReader(InputStreamReader(FileInputStream(offline)))
            val result: LinkedHashMap<String, ZumpaThread> = gson.fromJson(jsonReader, type)
            zumpaOfflineApi.offlineData = result
        }
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
        val picasso = Picasso.Builder(this)
                .downloader(PicassoHttpDownloader.createDefault(this, zumpaHttpClient, zumpaPrefs))
                .listener({ picasso, uri, exception ->
                    Log.d("PicassoLoader", "URL:%s Exception:%s".format(uri, exception))
                    exception.printStackTrace()
                }).build()
        Picasso.setSingletonInstance(picasso)
    }

    public val zumpaAPI: ZumpaAPI
        get() {
            return if(zumpaPrefs.isOffline) zumpaOfflineApi else zumpaOnlineAPI
        }

    public val zumpaOnlineAPI: ZumpaAPI by lazy {
        val retrofit = Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
                .addConverterFactory(ZumpaConverterFactory(zumpaParser))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .client(zumpaHttpClient)
                .build()

        retrofit.create(ZumpaAPI::class.java)
    }

    public val zumpaOfflineApi: ZumpaOfflineApi by lazy {
        ZumpaOfflineApi(LinkedHashMap<String, ZumpaThread>())
    }

    public val zumpaWebServiceAPI: ZumpaWSAPI by lazy {
        val retrofit = Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_WS_MAIN_URL)
                .addConverterFactory(ZumpaGenericConverterFactory())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .client(zumpaHttpClient)
                .build()

        retrofit.create(ZumpaWSAPI::class.java)
    }

    public val zumpaPHPAPI: ZumpaPHPAPI by lazy {
        val retrofit = Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_PHP_MAIN_URL)
                .addConverterFactory(ZumpaGenericConverterFactory())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .client(zumpaHttpClient)
                .build()

        retrofit.create(ZumpaPHPAPI::class.java)
    }

    public fun resetCookies() {
        zumpaHttpClient.execOn {
            setCookieHandler(CookieManager())
        }
    }

    public var followRedirects: Boolean
        get() = zumpaHttpClient.followRedirects ?: false
        set(value) {
            zumpaHttpClient.followRedirects = value
        }

}