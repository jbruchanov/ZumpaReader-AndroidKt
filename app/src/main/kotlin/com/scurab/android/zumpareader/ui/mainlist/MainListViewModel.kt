package com.scurab.android.zumpareader.ui.mainlist

import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.DeviceConfig
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.repository.AppEvent
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.SelectedThreadStore
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import kotlinx.coroutines.launch

/**
 * The order matters: the ordinal is the level of the LevelListDrawable behind the state bar on a
 * row, so it has to keep matching the old `ZumpaThread.STATE_*` constants.
 */
enum class ThreadState { None, New, Updated, Own, ResponseForYou }

data class ThreadRowUiState(
    val id: String,
    /** Raw markup - rendered by [com.scurab.android.zumpareader.text.ZumpaTextRenderer]. */
    val subject: String,
    val author: String,
    val lastAuthor: String?,
    val answerCount: Int,
    val time: Long,
    /** The list shows only the time when a last author is present - there is no room for both. */
    val useShortTimeFormat: Boolean,
    val state: ThreadState,
    val isFavorite: Boolean,
    val isSelected: Boolean,
)

data class MainListUiState(
    val rows: List<ThreadRowUiState> = emptyList(),
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val isLoggedIn: Boolean = false,
    /** Everything that writes needs a session and a connection. */
    val canInteract: Boolean = false,
)

sealed interface MainListEffect : UiEffect {
    /** Phone only - on a tablet the selection goes through [SelectedThreadStore] instead. */
    data class OpenThread(val threadId: String) : MainListEffect
    data class ShareThread(val link: String) : MainListEffect
    data object OpenSettings : MainListEffect
    data object OpenPostDialog : MainListEffect
    data object ShowOfflineDownloadDialog : MainListEffect
}

class MainListViewModel(
    private val threads: ZumpaThreadRepository,
    private val settings: ZumpaSettingsRepository,
    private val readStates: ZumpaReadStateRepository,
    private val selectedThread: SelectedThreadStore,
    private val eventBus: AppEventBus,
    private val device: DeviceConfig,
) : BaseViewModel<MainListUiState>(MainListUiState()) {

    /**
     * What this screen currently shows, which is not the same as everything the repository has
     * loaded - changing the filter or the offline switch starts the list over.
     */
    private val loaded = LinkedHashMap<String, ZumpaThread>()

    /**
     * The read/new/updated decoration used to be a field mutated on the shared ZumpaThread. It is
     * kept here so the row models stay immutable, and because the original deliberately leaves the
     * previous value in place when `items < readCount` - which only makes sense with a memory.
     */
    private val rowStates = HashMap<String, ThreadState>()

    private var nextThreadId: String? = null
    private var lastFilter: String? = null
    private var lastOffline: Boolean? = null
    private var isFirstLoad = true

    init {
        viewModelScope.launch {
            settings.isOffline.collect { offline ->
                setState { copy(isOffline = offline) }
            }
        }
        viewModelScope.launch {
            settings.isLoggedIn.collect { loggedIn ->
                setState { copy(isLoggedIn = loggedIn) }
            }
        }
        viewModelScope.launch {
            settings.isLoggedInNotOffline.collect { canInteract ->
                setState { copy(canInteract = canInteract) }
            }
        }
        viewModelScope.launch {
            selectedThread.selected.collect { id ->
                setState { copy(rows = rows.map { it.copy(isSelected = it.id == id) }) }
            }
        }
        viewModelScope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.OfflineDataChanged -> {
                        lastOffline = null
                        load()
                    }

                    is AppEvent.ContentPosted -> load()
                }
            }
        }
        load(isFirstLoad = true)
    }

    fun onRefresh() = load()

    fun onLoadMore() {
        val next = nextThreadId
        //the offline api answers with an empty next id, which is the end of the list
        if (state.isLoading || next.isNullOrEmpty()) {
            return
        }
        load(fromThread = next)
    }

    private fun load(fromThread: String? = null, isFirstLoad: Boolean = false) {
        if (state.isLoading) {
            return
        }
        val filter = settings.filter.value
        val offline = settings.isOffline.value
        if (lastFilter != filter || lastOffline != offline) {
            loaded.clear()
            rowStates.clear()
        }
        lastFilter = filter
        lastOffline = offline
        setState { copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val result = threads.loadMainPage(fromThread, filter)
                nextThreadId = result.nextThreadId
                loaded.putAll(result.items)
                //the decoration is recomputed for the whole page on every load, as it was
                val userName = settings.loggedUserName.value
                result.items.values.forEach { thread ->
                    rowStates[thread.id] =
                        thread.stateFor(readStates.readCount(thread.id), userName)
                }
                publishRows()
                if (isFirstLoad && device.isTablet) {
                    threads.lastThread()?.let { selectedThread.select(it.id) }
                }
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    fun onThreadClick(threadId: String) {
        val thread = loaded[threadId] ?: return
        //opening a thread marks everything in it as seen
        rowStates[threadId] = thread.stateFor(
            readCount = thread.items,
            userName = settings.loggedUserName.value,
        )
        publishRows()
        if (device.isTablet) {
            selectedThread.select(threadId)
        } else {
            effect(MainListEffect.OpenThread(threadId))
        }
    }

    fun onFavoriteClick(threadId: String) {
        if (!state.canInteract) return
        viewModelScope.launch {
            try {
                threads.toggleFavorite(threadId)
                publishRows()
            } catch (err: Throwable) {
                onError(err)
            }
        }
    }

    fun onIgnoreClick(threadId: String) {
        if (!state.canInteract) return
        viewModelScope.launch {
            try {
                threads.toggleIgnore(threadId)
                loaded.remove(threadId)
                rowStates.remove(threadId)
                publishRows()
            } catch (err: Throwable) {
                onError(err)
            }
        }
    }

    fun onShareClick(threadId: String) {
        if (!state.canInteract) return
        effect(MainListEffect.ShareThread(ZR.Constants.ZUMPA_THREAD_LINK.format(threadId)))
    }

    fun onOfflineToggle() {
        val goingOffline = !settings.isOffline.value
        settings.setOffline(goingOffline)
        if (goingOffline) {
            //the dialog fills the offline store, the reload happens on AppEvent.OfflineDataChanged
            lastOffline = null
            effect(MainListEffect.ShowOfflineDownloadDialog)
        } else {
            load()
        }
    }

    fun onSettingsClick() = effect(MainListEffect.OpenSettings)

    fun onFabClick() = effect(MainListEffect.OpenPostDialog)

    private fun publishRows() {
        val selectedId = selectedThread.selected.value
        val rows = loaded.values
            .sortedByDescending { it.idLong }
            .map { thread ->
                ThreadRowUiState(
                    id = thread.id,
                    subject = thread.subject,
                    author = thread.author,
                    lastAuthor = thread.lastAuthor,
                    answerCount = thread.items,
                    time = thread.time,
                    useShortTimeFormat = thread.lastAuthor != null,
                    state = rowStates[thread.id] ?: ThreadState.New,
                    isFavorite = thread.isFavorite,
                    isSelected = thread.id == selectedId,
                )
            }
        setState { copy(rows = rows) }
    }

    /**
     * The original `ZumpaThread.setStateBasedOnReadValue` as a pure function. The `items < readCount`
     * case has no branch there either - it leaves the previous value alone, which the comment
     * attributes to offline mode - so it returns [current] here.
     */
    private fun ZumpaThread.stateFor(
        readCount: Int?,
        userName: String?,
        current: ThreadState = rowStates[id] ?: ThreadState.New,
    ): ThreadState = when {
        hasResponseForYou -> ThreadState.ResponseForYou
        readCount == null -> ThreadState.New
        items == readCount -> if (userName != null && userName == author) ThreadState.Own else ThreadState.None
        items > readCount -> ThreadState.Updated
        else -> current
    }
}
