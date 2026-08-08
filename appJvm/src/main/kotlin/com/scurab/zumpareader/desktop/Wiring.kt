package com.scurab.zumpareader.desktop

import com.scurab.android.zumpareader.data.ZumpaApiImpl
import com.scurab.android.zumpareader.data.buildZumpaHttpClient
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.CookieRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepositoryImpl
import com.scurab.android.zumpareader.util.InMemoryKeyValueStore
import com.scurab.android.zumpareader.util.ZumpaPrefs
import io.ktor.client.engine.okhttp.OkHttp

/**
 * The whole desktop object graph, by hand.
 *
 * No Koin here on purpose: the point of this module is to show what `:shared` needs from a host, and
 * six constructor calls say that more plainly than a DI module would. Every type below comes from
 * `commonMain` except the two that are the platform's job - the http engine and the key-value store.
 */
class Wiring {

    /**
     * Nothing persists between runs yet. `InMemoryKeyValueStore` lives in `shared/jvmMain` and is
     * the desktop stand-in for `SharedPreferencesStore`; a file-backed one would drop straight in
     * without anything above it changing.
     */
    private val prefs = ZumpaPrefs(InMemoryKeyValueStore())

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

    private val api = ZumpaApiImpl(httpClient, parser)

    val threads: ZumpaThreadRepository = ZumpaThreadRepositoryImpl(api = { api })
}
