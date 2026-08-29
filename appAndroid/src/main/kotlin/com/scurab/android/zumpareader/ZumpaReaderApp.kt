package com.scurab.android.zumpareader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.scurab.android.zumpareader.di.appModules
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.usecase.InitAppUseCase
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

    private val readStateRepository: ZumpaReadStateRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@ZumpaReaderApp)
            modules(appModules)
        }
        //the whole startup list, per platform - see AndroidInitAppUseCase. The offline snapshot is
        //not among it: the api factory reads it lazily, so a toggle into offline mode picks it up
        //and an online start does not pay for the parse.
        get<InitAppUseCase>()()

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
    }
}
