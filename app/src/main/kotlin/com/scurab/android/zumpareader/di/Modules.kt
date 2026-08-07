package com.scurab.android.zumpareader.di

import com.google.gson.Gson
import com.scurab.android.zumpareader.BuildConfig
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.app.MainViewModel
import com.scurab.android.zumpareader.arch.DeviceConfig
import com.scurab.android.zumpareader.ZumpaOfflineApi
import com.scurab.android.zumpareader.ZumpaPHPAPI
import com.scurab.android.zumpareader.ZumpaWSAPI
import com.scurab.android.zumpareader.data.ZumpaConverterFactory
import com.scurab.android.zumpareader.data.ZumpaGenericConverterFactory
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.SelectedThreadStore
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepositoryImpl
import com.scurab.android.zumpareader.util.ZumpaPrefs
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * The online API, kept behind a qualifier because the unqualified [ZumpaAPI] is the
 * online/offline switch below.
 */
val ONLINE_API = named("onlineApi")

private const val TIMEOUT = 5000L

val coreModule = module {
    single { ZumpaPrefs(androidContext()) }
    single { CookieManager() }
    single { Gson() }
    single { DeviceConfig(isTablet = androidContext().resources.getBoolean(R.bool.is_tablet)) }
    single {
        val prefs = get<ZumpaPrefs>()
        ZumpaSimpleParser().apply {
            userName = prefs.loggedUserName
            isShowLastUser = prefs.showLastAuthor
        }
    }

    single { ZumpaSettingsRepository(get()) }
    single { ZumpaReadStateRepository(get(), get()) }
    single { SelectedThreadStore() }
    single { AppEventBus() }

    /**
     * `api = { get() }` and not `api = get()`: the unqualified [ZumpaAPI] below is a factory, so
     * handing this singleton an instance would freeze the online/offline choice forever.
     */
    single<ZumpaThreadRepository> { ZumpaThreadRepositoryImpl(api = { get() }) }
}

val networkModule = module {
    single { buildHttpClient(get(), get()) }

    single(ONLINE_API) {
        Retrofit.Builder()
            .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
            .addConverterFactory(ZumpaConverterFactory(get()))
            .client(get())
            .build()
            .create(ZumpaAPI::class.java)
    }

    single { ZumpaOfflineApi(LinkedHashMap()) }

    /**
     * A factory on purpose: the offline switch is a runtime setting, so every injection point
     * has to be re-evaluated. Anything holding on to the result - a ViewModel, for instance -
     * keeps the API it was given at construction time.
     */
    factory<ZumpaAPI> {
        if (get<ZumpaPrefs>().isOffline) get<ZumpaOfflineApi>() else get(ONLINE_API)
    }

    single {
        Retrofit.Builder()
            .baseUrl(ZR.Constants.ZUMPA_WS_MAIN_URL)
            .addConverterFactory(ZumpaGenericConverterFactory())
            .client(get())
            .build()
            .create(ZumpaWSAPI::class.java)
    }

    single {
        Retrofit.Builder()
            .baseUrl(ZR.Constants.ZUMPA_PHP_MAIN_URL)
            .addConverterFactory(ZumpaGenericConverterFactory())
            .client(get())
            .build()
            .create(ZumpaPHPAPI::class.java)
    }
}

/**
 * Empty until the screens move to MVVM. Each screen adds its own line here:
 *
 * `viewModel { MainListViewModel(get(), get()) }`
 *
 * and the fragment picks it up with `private val viewModel: MainListViewModel by viewModel()`
 * from `org.koin.androidx.viewmodel.ext.android`.
 */
val viewModelModule = module {
    viewModel { MainViewModel(get()) }
}

val appModules = listOf(coreModule, networkModule, viewModelModule)

private fun buildHttpClient(cookieManager: CookieManager, zumpaPrefs: ZumpaPrefs): OkHttpClient {
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    cookieManager.put(URI.create(ZR.Constants.ZUMPA_MAIN_URL), zumpaPrefs.cookiesMap)

    val logging = HttpLoggingInterceptor()
    // set your desired log level
    logging.level = HttpLoggingInterceptor.Level.BODY

    return OkHttpClient.Builder().apply {
        followRedirects(false)
        cache(null)
        connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
        readTimeout(TIMEOUT * 5, TimeUnit.MILLISECONDS)
        writeTimeout(TIMEOUT * 5, TimeUnit.MILLISECONDS)
        cookieJar(JavaNetCookieJar(cookieManager))
        addNetworkInterceptor { chain ->
            val req = chain.request()
            val rb = req
                .newBuilder()
                .addHeader("Cache-Control", "max-age=0")
                .url(req.url.newBuilder().addQueryParameter("_ts", System.currentTimeMillis().toString()).build())

            chain.proceed(rb.build())
        }
        if (BuildConfig.DEBUG) {
            addNetworkInterceptor(logging)
        }
    }.build()
}
