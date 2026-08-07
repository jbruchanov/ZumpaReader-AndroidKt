package com.scurab.android.zumpareader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.facebook.drawee.backends.pipeline.Fresco
import com.google.firebase.FirebaseApp
import com.google.gson.Gson
import com.scurab.android.zumpareader.data.PicassoHttpDownloader2
import com.scurab.android.zumpareader.di.ONLINE_API
import com.scurab.android.zumpareader.di.appModules
import com.scurab.android.zumpareader.model.ZumpaReadState
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.OfflineDataRepository
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.usecase.CreateNotificationChannelsUseCase
import com.scurab.android.zumpareader.util.ZumpaPrefs
import com.squareup.picasso.Picasso
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

    //everything below is built by koin now, see di/Modules.kt, these are kept so the existing
    //`app().zumpaSomething` call sites keep working; new code should inject what it needs
    val zumpaParser: ZumpaSimpleParser by inject()
    val zumpaPrefs: ZumpaPrefs by inject()
    val cookieManager: CookieManager by inject()
    private val gson: Gson by inject()
    val zumpaHttpClient: OkHttpClient by inject()

    private val threadRepository: ZumpaThreadRepository by inject()
    private val readStateRepository: ZumpaReadStateRepository by inject()
    private val offlineData: OfflineDataRepository by inject()

    //transitional accessors, the repositories own these now - see MVVM_PLAN.md phase 1.
    //both delegate to the repository's backing map, so a not-yet-migrated screen writing here is
    //visible to a not-yet-migrated screen reading here. Deleted in phase 8.
    val zumpaData: TreeMap<String, ZumpaThread> get() = threadRepository.rawThreads

    val zumpaReadStates: TreeMap<String, ZumpaReadState> get() = readStateRepository.raw

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@ZumpaReaderApp)
            modules(appModules)
        }
        CreateNotificationChannelsUseCase(this)()

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
                    readStateRepository.persist()
                }
            }
        })
        offlineData.loadFromDisk()
        FirebaseApp.initializeApp(this)
        if (zumpaPrefs.userId == null) {
            zumpaPrefs.userId = UUID.randomUUID().toString()
        }
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
