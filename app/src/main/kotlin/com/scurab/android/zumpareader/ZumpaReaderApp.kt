package com.scurab.android.zumpareader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Environment
import android.util.Log
import com.facebook.drawee.backends.pipeline.Fresco
import com.github.salomonbrys.kotson.DeserializerArg
import com.github.salomonbrys.kotson.registerTypeAdapter
import com.google.firebase.FirebaseApp
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.scurab.android.zumpareader.data.PicassoHttpDownloader2
import com.scurab.android.zumpareader.di.ONLINE_API
import com.scurab.android.zumpareader.di.appModules
import com.scurab.android.zumpareader.gson.GsonExcludeStrategy
import com.scurab.android.zumpareader.model.ZumpaReadState
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.usecase.CreateNotificationChannelsUseCase
import com.scurab.android.zumpareader.util.ZumpaPrefs
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.CookieManager
import java.util.*
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Created by JBruchanov on 24/11/2015.
 */
class ZumpaReaderApp : Application() {

    companion object {
        val OFFLINE_FILE_NAME = "offline.json"
    }

    //everything below is built by koin now, see di/Modules.kt, these are kept so the existing
    //`app().zumpaSomething` call sites keep working; new code should inject what it needs
    val zumpaParser: ZumpaSimpleParser by inject()
    val zumpaPrefs: ZumpaPrefs by inject()
    val cookieManager: CookieManager by inject()
    private val gson: Gson by inject()
    val zumpaHttpClient: OkHttpClient by inject()

    val zumpaData: TreeMap<String, ZumpaThread> = TreeMap()

    var zumpaReadStates: TreeMap<String, ZumpaReadState> = TreeMap()
        private set

    private val MAX_STATES_TO_STORE = 100

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@ZumpaReaderApp)
            modules(appModules)
        }
        CreateNotificationChannelsUseCase(this)()
        loadReadStates()

        initPicasso()
        Fresco.initialize(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var activities = 0
            override fun onActivityStarted(activity: Activity) {
                activities++
            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
                activities--
                if (activities == 0) {
                    storeReadStates()
                }
            }
        })
        loadOfflineData()
        FirebaseApp.initializeApp(this)
        if (zumpaPrefs.userId == null) {
            zumpaPrefs.userId = UUID.randomUUID().toString()
        }
    }

    fun loadOfflineData() {
        val offline = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), OFFLINE_FILE_NAME)
        if (offline.exists() && zumpaPrefs.isOffline) {
            val gsonBuilder = GsonBuilder().setExclusionStrategies(GsonExcludeStrategy())
            gsonBuilder.registerTypeAdapter<ZumpaThread> {
                deserialize { elem ->
                    if (elem is DeserializerArg) {
                        ZumpaThread.thread(elem.json as JsonObject)
                    } else {
                        ZumpaThread.thread(elem as JsonObject)
                    }
                }
            }
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
            zumpaReadStates = gson.fromJson(json, object : TypeToken<TreeMap<String, ZumpaReadState>>() {}.type)
        }
    }

    private fun storeReadStates() {
        var toStore: Map<String, ZumpaReadState> = zumpaReadStates
        if (zumpaReadStates.size > MAX_STATES_TO_STORE) {
            var iterator = zumpaReadStates.descendingKeySet().iterator()
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
            .downloader(PicassoHttpDownloader2.createDefault(this, zumpaHttpClient, zumpaPrefs))
            .listener({ picasso, uri, exception ->
                Log.d("PicassoLoader", "URL:%s Exception:%s".format(uri, exception))
                exception.printStackTrace()
            }).build()
        Picasso.setSingletonInstance(picasso)
    }

    /**
     * Resolved on every access, the definition picks online or offline by the current setting.
     */
    val zumpaAPI: ZumpaAPI get() = get()

    val zumpaOnlineAPI: ZumpaAPI by inject(ONLINE_API)
    val zumpaOfflineApi: ZumpaOfflineApi by inject()
    val zumpaWebServiceAPI: ZumpaWSAPI by inject()
    val zumpaPHPAPI: ZumpaPHPAPI by inject()

    fun resetCookies() {
        cookieManager.cookieStore.removeAll()
    }
}
