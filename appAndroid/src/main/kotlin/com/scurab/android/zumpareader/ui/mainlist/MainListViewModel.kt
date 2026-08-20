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
    /**
     * The swipe-to-reveal row menu. This used to be translationX on the view holder, reset on every
     * rebind - which is why the menu snapped shut whenever the list refreshed. It is state now.
     */
    val isMenuOpen: Boolean,
)

data class MainListUiState(
    val rows: List<ThreadRowUiState> = emptyList(),
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val isLoggedIn: Boolean = false,
    /** Everything that writes needs a session and a connection. */
    val canInteract: Boolean = false,
)

interface MainListEventHandler {
    fun onRefreshRequested()
    fun onEndReached()
    fun onThreadClicked(threadId: String)
    fun onThreadLongPressed(threadId: String)
    fun onFavoriteClicked(threadId: String)
    fun onIgnoreClicked(threadId: String)
    fun onShareClicked(threadId: String)
    fun onOfflineToggled()
    fun onSettingsClicked()
    fun onFabClicked()
}

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
) : BaseViewModel<MainListUiState>(MainListUiState()), MainListEventHandler {

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
    private var openMenuId: String? = null

    init {
        viewModelScope.launch {
            settings.isOffline.collect { offline ->
                setState { copy(isOffline = offline) }
                //the switch changes where the list comes from, so the list has to be read again.
                //This is the only trigger: the download dialog is not the only way into offline
                //mode - Settings has the same switch - and a download that is dismissed or fails
                //used to leave the list showing nothing at all.
                if (lastOffline != null && lastOffline != offline) {
                    load()
                }
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

    override fun onRefreshRequested() = load()

    override fun onEndReached() {
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

    override fun onThreadClicked(threadId: String) {
        val thread = loaded[threadId] ?: return
        //opening a thread marks everything in it as seen
        rowStates[threadId] = thread.stateFor(
            readCount = thread.items,
            userName = settings.loggedUserName.value,
        )
        openMenuId = null
        publishRows()
        if (device.isTablet) {
            selectedThread.select(threadId)
        } else {
            effect(MainListEffect.OpenThread(threadId))
        }
    }

    override fun onFavoriteClicked(threadId: String) {
        if (!state.canInteract) return
        closeMenu()
        viewModelScope.launch {
            try {
                threads.toggleFavorite(threadId)
                publishRows()
            } catch (err: Throwable) {
                onError(err)
            }
        }
    }

    override fun onIgnoreClicked(threadId: String) {
        if (!state.canInteract) return
        closeMenu()
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

    override fun onShareClicked(threadId: String) {
        if (!state.canInteract) return
        closeMenu()
        effect(MainListEffect.ShareThread(ZR.Constants.ZUMPA_THREAD_LINK.format(threadId)))
    }

    override fun onOfflineToggled() {
        val goingOffline = !settings.isOffline.value
        settings.setOffline(goingOffline)
        //the reload is driven by the isOffline collector above, either way. All this adds is the
        //offer to refresh the snapshot on the way in.
        if (goingOffline) {
            effect(MainListEffect.ShowOfflineDownloadDialog)
        }
    }

    override fun onSettingsClicked() = effect(MainListEffect.OpenSettings)

    override fun onFabClicked() = effect(MainListEffect.OpenPostDialog)

    /** One row menu open at a time, and any action on it closes it. */
    override fun onThreadLongPressed(threadId: String) {
        if (!state.canInteract) return
        openMenuId = if (openMenuId == threadId) null else threadId
        publishRows()
    }

    private fun closeMenu() {
        if (openMenuId != null) {
            openMenuId = null
            publishRows()
        }
    }

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
                    isMenuOpen = thread.id == openMenuId,
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
