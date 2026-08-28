package com.scurab.zumpareader.desktop

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.ZumpaOfflineApi
import com.scurab.android.zumpareader.ZumpaPHPAPI
import com.scurab.android.zumpareader.data.ZumpaApiImpl
import com.scurab.android.zumpareader.data.ZumpaPHPApiImpl
import com.scurab.android.zumpareader.data.buildImageHttpClient
import com.scurab.android.zumpareader.data.buildZumpaHttpClient
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.AuthRepository
import com.scurab.android.zumpareader.repository.CookieRepository
import com.scurab.android.zumpareader.repository.ImagePrefetcher
import com.scurab.android.zumpareader.repository.InMemorySentDraftRepository
import com.scurab.android.zumpareader.repository.NoImagePrefetcher
import com.scurab.android.zumpareader.repository.NoPushTokenProvider
import com.scurab.android.zumpareader.repository.OfflineDataRepository
import com.scurab.android.zumpareader.repository.PushTokenProvider
import com.scurab.android.zumpareader.repository.SentDraftRepository
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepositoryImpl
import com.scurab.android.zumpareader.usecase.InitAppUseCase
import com.scurab.android.zumpareader.usecase.OfflineDownloadUseCase
import com.scurab.android.zumpareader.util.KeyValueStore
import com.scurab.android.zumpareader.util.ZumpaPrefs
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/** The online api, qualified because the unqualified [ZumpaAPI] is the one that switches. */
internal val ONLINE_API = named("online")

/** The client images go over - see `buildImageHttpClient` for why it is not the api one. */
internal val IMAGE_CLIENT = named("images")

/**
 * The desktop object graph.
 *
 * The same shape as `:appAndroid`'s module, down to the qualifier on the online api and the factory
 * that resolves the switching one, so the two hosts can be read against each other. What differs is
 * only what the platform has to answer for: the http engine, the key-value store, the two seams
 * `:shared` declares for push and an image cache - `NoPushTokenProvider` and `NoImagePrefetcher`,
 * which exist in `commonMain` for a host that has neither - and the startup work, which is
 * `InitAppUseCase`.
 */
internal fun desktopModule(home: File = defaultHome()) = module {

    single {
        Json {
            //the forum's web service adds fields without warning, and a snapshot written by a
            //newer build has to stay readable by an older one
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    /**
     * File-backed, unlike the jvm target's `InMemoryKeyValueStore` - whose own comment says a real
     * desktop build would want this. A session that died with the process would make the sign-in
     * something to repeat every launch.
     */
    single<KeyValueStore> { FileKeyValueStore(File(home, PREFS_FILE_NAME)) }

    single { ZumpaPrefs(get()) }

    single { CookieRepository(get()) }

    single { ZumpaSettingsRepository(get()) }

    /**
     * How much of each thread has been read, which is the whole input to the coloured state bar down
     * the left of a list row. Missing from this graph, which is why the desktop list had no bars.
     *
     * [ZumpaReadStateRepository.persist] is not called from here - see the window's close handler in
     * `main`, which is this host's equivalent of the last activity stopping.
     */
    single { ZumpaReadStateRepository(get(), get()) }

    single {
        val prefs = get<ZumpaPrefs>()
        ZumpaSimpleParser().apply {
            userName = prefs.loggedUserName
            isShowLastUser = prefs.showLastAuthor
        }
    }

    single<HttpClient> {
        buildZumpaHttpClient(engine = OkHttp.create(), cookies = get(), isDebug = false)
    }

    single<ZumpaAPI>(ONLINE_API) { ZumpaApiImpl(get(), get()) }

    single<ZumpaPHPAPI> { ZumpaPHPApiImpl(get()) }

    single { ZumpaOfflineApi(LinkedHashMap()) }

    single {
        OfflineDataRepository(
            snapshotPath = File(home, OfflineDataRepository.OFFLINE_FILE_NAME).absolutePath,
            offlineApi = get(),
            json = get(),
        )
    }

    single<HttpClient>(IMAGE_CLIENT) { buildImageHttpClient(OkHttp.create(), get()) }

    /**
     * Coil over the app's own client, so an image request carries the same cookies as everything
     * else - the same arrangement `:appAndroid`'s `buildImageLoader` makes. Coil would find the
     * ktor fetcher on the classpath by itself and build a client of its own, which would work and
     * would not be signed in.
     */
    single {
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = get<HttpClient>(IMAGE_CLIENT)))
            }
            .build()
    }

    single<PushTokenProvider> { NoPushTokenProvider }

    single<ImagePrefetcher> { NoImagePrefetcher }

    //in memory, not the file-backed store the settings use: this is a safety net for the session
    //you are in, and one that survived a restart would offer something written days ago
    single<SentDraftRepository> { InMemorySentDraftRepository() }

    //the desktop half of the startup seam - see DesktopInitAppUseCase
    single<InitAppUseCase> { DesktopInitAppUseCase(imageLoader = { get() }) }

    single {
        AuthRepository(
            onlineApi = get(ONLINE_API),
            phpApi = get(),
            prefs = get(),
            parser = get(),
            cookies = get(),
            pushTokens = get(),
        )
    }

    /** The online api on purpose: a download must not read the snapshot it is replacing. */
    single { OfflineDownloadUseCase(get(ONLINE_API), get(), get()) }

    /**
     * A factory, not a single: the offline switch is a runtime setting, so anything resolving this
     * once would keep the api it was handed at construction time. Resolving per call is what lets
     * the switch take effect without rebuilding the repository above it.
     */
    factory<ZumpaAPI> {
        val prefs = get<ZumpaPrefs>()
        if (prefs.isOffline) {
            get<OfflineDataRepository>().ensureLoaded()
            get<ZumpaOfflineApi>()
        } else {
            get(ONLINE_API)
        }
    }

    /** `api = { get() }` and not `api = get()`, for the reason above. */
    single<ZumpaThreadRepository> { ZumpaThreadRepositoryImpl(api = { get() }) }
}

/** Where a session and an offline snapshot live between runs. */
internal fun defaultHome(): File = File(System.getProperty("user.home"), ".zumpareader")

private const val PREFS_FILE_NAME = "prefs.properties"
