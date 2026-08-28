package com.scurab.zumpareader.desktop

import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.ZumpaOfflineApi
import com.scurab.android.zumpareader.data.ZumpaApiImpl
import com.scurab.android.zumpareader.data.ZumpaPHPApiImpl
import com.scurab.android.zumpareader.data.buildZumpaHttpClient
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.AuthRepository
import com.scurab.android.zumpareader.repository.CookieRepository
import com.scurab.android.zumpareader.repository.NoImagePrefetcher
import com.scurab.android.zumpareader.repository.NoPushTokenProvider
import com.scurab.android.zumpareader.repository.OfflineDataRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepositoryImpl
import com.scurab.android.zumpareader.usecase.OfflineDownloadUseCase
import com.scurab.android.zumpareader.util.ZumpaPrefs
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The whole desktop object graph, by hand.
 *
 * No Koin here on purpose: the point of this module is to show what `:shared` needs from a host,
 * and constructor calls say that more plainly than a DI module would. Everything below comes from
 * `commonMain` except the four the platform has to answer for: the http engine, the key-value
 * store, and the two seams `:shared` declares for push and for an image cache, neither of which a
 * desktop build has. `NoPushTokenProvider` and `NoImagePrefetcher` exist in `commonMain` for this.
 */
class Wiring {

    private val json = Json {
        //the forum's web service adds fields without warning, and an offline snapshot written by a
        //newer build has to stay readable by an older one
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** `~/.zumpareader`, so a login and an offline snapshot outlive the process. */
    private val home = File(System.getProperty("user.home"), ".zumpareader")

    private val prefs = ZumpaPrefs(FileKeyValueStore(File(home, "prefs.properties")))

    private val cookies = CookieRepository(prefs)

    private val parser = ZumpaSimpleParser().apply {
        userName = prefs.loggedUserName
        isShowLastUser = prefs.showLastAuthor
    }

    private val httpClient = buildZumpaHttpClient(
        engine = OkHttp.create(),
        cookies = cookies,
        isDebug = false,
    )

    private val onlineApi: ZumpaAPI = ZumpaApiImpl(httpClient, parser)

    private val offlineApi = ZumpaOfflineApi(LinkedHashMap())

    private val offlineData = OfflineDataRepository(
        snapshotPath = File(home, OfflineDataRepository.OFFLINE_FILE_NAME).absolutePath,
        offlineApi = offlineApi,
        json = json,
    )

    val settings = ZumpaSettingsRepository(prefs)

    val auth = AuthRepository(
        onlineApi = onlineApi,
        phpApi = ZumpaPHPApiImpl(httpClient),
        prefs = prefs,
        parser = parser,
        cookies = cookies,
        pushTokens = NoPushTokenProvider,
    )

    /** The online api on purpose: a download must not read the snapshot it is replacing. */
    val downloader = OfflineDownloadUseCase(onlineApi, NoImagePrefetcher, json)

    /**
     * Resolved per call, not once. The offline switch is a runtime setting, so anything holding an
     * api from construction time would keep the one it was handed - the same reason the Android
     * module binds the unqualified api as a factory.
     */
    val threads: ZumpaThreadRepository = ZumpaThreadRepositoryImpl(
        api = {
            if (prefs.isOffline) {
                offlineData.ensureLoaded()
                offlineApi
            } else {
                onlineApi
            }
        },
    )

    fun setOffline(offline: Boolean) {
        prefs.isOffline = offline
    }

    val offlineSnapshotPath: String get() = offlineData.path

    /** What the download landing does: the api serves it, and the list is rebuilt from it. */
    fun applyDownloaded(data: LinkedHashMap<String, ZumpaThread>) {
        offlineData.setData(data)
        threads.replaceAll(data)
    }
}
