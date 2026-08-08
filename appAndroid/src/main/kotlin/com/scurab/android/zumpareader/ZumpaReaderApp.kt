package com.scurab.android.zumpareader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.scurab.android.zumpareader.di.ONLINE_API
import com.scurab.android.zumpareader.di.appModules
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.OfflineDataRepository
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.usecase.CreateNotificationChannelsUseCase
import com.scurab.android.zumpareader.util.ZumpaPrefs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
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

    private val readStateRepository: ZumpaReadStateRepository by inject()
    private val offlineData: OfflineDataRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@ZumpaReaderApp)
            modules(appModules)
        }
        CreateNotificationChannelsUseCase(this)()


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
            //kotlin.uuid rather than java.util.UUID - same hyphenated form, no jvm dependency
            @OptIn(ExperimentalUuidApi::class)
            zumpaPrefs.userId = Uuid.random().toString()
        }
    }

    /**
     * Resolved on every access, the definition picks online or offline by the current setting.
     */
    val zumpaAPI: ZumpaAPI get() = get()

    val zumpaOnlineAPI: ZumpaAPI by inject(ONLINE_API)
    val zumpaPHPAPI: ZumpaPHPAPI by inject()
}
