package com.scurab.android.zumpareader.di

import com.scurab.android.zumpareader.BuildConfig
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.ZumpaOfflineApi
import com.scurab.android.zumpareader.ZumpaPHPAPI
import com.scurab.android.zumpareader.arch.DeviceConfig
import android.content.Context
import android.os.Environment
import com.scurab.android.zumpareader.repository.CoilImagePrefetcher
import com.scurab.android.zumpareader.repository.FirebasePushTokenProvider
import com.scurab.android.zumpareader.repository.ImagePrefetcher
import com.scurab.android.zumpareader.repository.PushTokenProvider
import com.scurab.android.zumpareader.util.KeyValueStore
import com.scurab.android.zumpareader.util.SharedPreferencesStore
import java.io.File
import com.scurab.android.zumpareader.data.ZumpaApiImpl
import com.scurab.android.zumpareader.data.ZumpaPHPApiImpl
import com.scurab.android.zumpareader.data.buildZumpaHttpClient
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.AuthRepository
import com.scurab.android.zumpareader.repository.CookieRepository
import com.scurab.android.zumpareader.repository.ImageCacheRepository
import com.scurab.android.zumpareader.ui.compose.buildImageLoader
import com.scurab.android.zumpareader.repository.ImageUploadRepository
import com.scurab.android.zumpareader.repository.OfflineDataRepository
import com.scurab.android.zumpareader.repository.SelectedThreadStore
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepositoryImpl
import com.scurab.android.zumpareader.ui.image.ImageViewModel
import com.scurab.android.zumpareader.ui.main.MainViewModel
import com.scurab.android.zumpareader.ui.mainlist.MainListViewModel
import com.scurab.android.zumpareader.ui.offline.OfflineDownloadViewModel
import com.scurab.android.zumpareader.ui.post.PostImageViewModel
import com.scurab.android.zumpareader.ui.post.PostViewModel
import com.scurab.android.zumpareader.ui.settings.AndroidNotificationState
import com.scurab.android.zumpareader.ui.settings.NotificationState
import com.scurab.android.zumpareader.ui.settings.SettingsViewModel
import com.scurab.android.zumpareader.ui.sublist.SubListViewModel
import com.scurab.android.zumpareader.usecase.OfflineDownloadUseCase
import com.scurab.android.zumpareader.util.ZumpaPrefs
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * The online API, kept behind a qualifier because the unqualified [ZumpaAPI] is the
 * online/offline switch below.
 */
val ONLINE_API = named("onlineApi")


/**
 * Where the offline snapshot lives. The one genuinely Android-specific thing about it, which is why
 * [OfflineDataRepository] takes a path rather than a `Context`.
 */
private fun offlineSnapshotPath(context: Context): String =
    File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        OfflineDataRepository.OFFLINE_FILE_NAME,
    ).absolutePath

val coreModule = module {
    //the android half of the shared KeyValueStore seam
    single<KeyValueStore> { SharedPreferencesStore(androidContext()) }
    single { ZumpaPrefs(get()) }
    single {
        Json {
            //the forum's web service adds fields without warning, and an offline snapshot written
            //by a newer build has to stay readable by an older one
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
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
    single { ImageUploadRepository(get()) }
    single { OfflineDataRepository(offlineSnapshotPath(androidContext()), get(), get()) }
    single<ImagePrefetcher> { CoilImagePrefetcher(androidContext(), get()) }
    //the online api explicitly - a download started in offline mode must not read the snapshot
    //it is about to replace
    single { OfflineDownloadUseCase(get(ONLINE_API), get(), get()) }
    single { ImageCacheRepository(androidContext(), get()) }
    single { buildImageLoader(androidContext(), get()) }
    single { CookieRepository(get()) }
    single<PushTokenProvider> { FirebasePushTokenProvider() }
    single { AuthRepository(get(ONLINE_API), get(), get(), get(), get(), get()) }
    single<NotificationState> { AndroidNotificationState(androidContext()) }

    /**
     * `api = { get() }` and not `api = get()`: the unqualified [ZumpaAPI] below is a factory, so
     * handing this singleton an instance would freeze the online/offline choice forever.
     */
    single<ZumpaThreadRepository> { ZumpaThreadRepositoryImpl(api = { get() }) }
}

val networkModule = module {
    single { buildZumpaHttpClient(OkHttp.create(), get(), isDebug = BuildConfig.DEBUG) }

    single<ZumpaAPI>(ONLINE_API) { ZumpaApiImpl(get(), get()) }

    single { ZumpaOfflineApi(LinkedHashMap()) }

    /**
     * A factory on purpose: the offline switch is a runtime setting, so every injection point
     * has to be re-evaluated. Anything holding on to the result - a ViewModel, for instance -
     * keeps the API it was given at construction time.
     */
    factory<ZumpaAPI> {
        if (get<ZumpaPrefs>().isOffline) {
            //here rather than at startup: this is the one place that knows offline mode is being
            //used, so a snapshot downloaded in an earlier session is picked up on a toggle too
            get<OfflineDataRepository>().ensureLoaded()
            get<ZumpaOfflineApi>()
        } else {
            get(ONLINE_API)
        }
    }

    single<ZumpaPHPAPI> { ZumpaPHPApiImpl(get()) }
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
    viewModel { MainListViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SubListViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { PostViewModel(get(), get(), get()) }
    viewModel { PostImageViewModel(androidContext(), get()) }
    viewModel { OfflineDownloadViewModel(get(), get(), get(), get()) }
    viewModel { ImageViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
}

val appModules = listOf(coreModule, networkModule, viewModelModule)

//the client, its cookie jar, its timeouts and its 502 retry all live in `buildZumpaHttpClient`
