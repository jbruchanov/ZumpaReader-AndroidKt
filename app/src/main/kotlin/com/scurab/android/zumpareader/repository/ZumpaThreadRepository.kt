package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaToggleBody
import com.scurab.android.zumpareader.model.ZumpaVoteSurveyBody
import com.scurab.android.zumpareader.util.ignoringZumpaRedirect
import com.scurab.android.zumpareader.util.retrying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.TreeMap

/**
 * The single owner of the loaded threads, replacing the `TreeMap` that used to live on the
 * Application. Also the only place that talks to [ZumpaAPI], so the retry and the
 * 302-is-a-success handling are written once instead of at every call site.
 */
interface ZumpaThreadRepository {

    val threads: StateFlow<Map<String, ZumpaThread>>

    fun thread(id: String): ZumpaThread?

    /** Highest key in the backing sorted map - what the tablet opens on first load. */
    fun lastThread(): ZumpaThread?

    suspend fun loadMainPage(fromThread: String?, filter: String): ZumpaMainPageResult

    suspend fun loadThread(id: String): List<ZumpaThreadItem>

    suspend fun toggleFavorite(id: String)

    suspend fun toggleIgnore(id: String)

    suspend fun sendThread(body: ZumpaThreadBody): Boolean

    suspend fun sendResponse(threadId: String, body: ZumpaThreadBody): Boolean

    suspend fun voteSurvey(body: ZumpaVoteSurveyBody)

    fun remove(id: String)

    fun replaceAll(data: Map<String, ZumpaThread>)
}

/**
 * [api] is a provider and not an instance on purpose. The unqualified [ZumpaAPI] binding is a koin
 * `factory` because the online/offline switch is a runtime setting, so anything that injects it
 * once keeps the api it was handed at construction time. Resolving per call is what makes toggling
 * offline mode take effect without recreating this repository.
 */
class ZumpaThreadRepositoryImpl(
    private val api: () -> ZumpaAPI,
) : ZumpaThreadRepository {

    private val store = TreeMap<String, ZumpaThread>()

    private val _threads = MutableStateFlow<Map<String, ZumpaThread>>(emptyMap())
    override val threads: StateFlow<Map<String, ZumpaThread>> = _threads.asStateFlow()

    override fun thread(id: String): ZumpaThread? = store[id]

    override fun lastThread(): ZumpaThread? = store.lastEntry()?.value

    override suspend fun loadMainPage(fromThread: String?, filter: String): ZumpaMainPageResult {
        val result = retrying {
            if (fromThread != null) api().getMainPage(fromThread, filter) else api().getMainPage(filter)
        }
        store.putAll(result.items)
        publish()
        return result
    }

    override suspend fun loadThread(id: String): List<ZumpaThreadItem> {
        return retrying { api().getThreadPage(id, id) }.items
    }

    override suspend fun toggleFavorite(id: String) {
        api().toggleRate(ZumpaToggleBody(id, ZumpaToggleBody.tFavorite))
        store[id]?.let { it.isFavorite = !it.isFavorite }
        publish()
    }

    /** Ignoring a thread takes it off the list for good, so it leaves the store too. */
    override suspend fun toggleIgnore(id: String) {
        api().toggleRate(ZumpaToggleBody(id, ZumpaToggleBody.tIgnore))
        remove(id)
    }

    override suspend fun sendThread(body: ZumpaThreadBody): Boolean {
        return ignoringZumpaRedirect { api().sendThread(body) }
    }

    override suspend fun sendResponse(threadId: String, body: ZumpaThreadBody): Boolean {
        return ignoringZumpaRedirect { api().sendResponse(threadId, threadId, body) }
    }

    override suspend fun voteSurvey(body: ZumpaVoteSurveyBody) {
        api().voteSurvey(body)
    }

    override fun remove(id: String) {
        store.remove(id)
        publish()
    }

    override fun replaceAll(data: Map<String, ZumpaThread>) {
        store.clear()
        store.putAll(data)
        publish()
    }

    private fun publish() {
        _threads.value = LinkedHashMap(store)
    }
}
