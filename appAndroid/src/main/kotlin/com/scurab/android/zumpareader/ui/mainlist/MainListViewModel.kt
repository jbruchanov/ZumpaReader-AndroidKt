package com.scurab.android.zumpareader.ui.mainlist

import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.arch.WindowLayout
import com.scurab.android.zumpareader.model.ThreadState
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.stateFor
import com.scurab.android.zumpareader.repository.AppEvent
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.SelectedThreadStore
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import kotlinx.coroutines.launch

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
    /**
     * A page being appended, as opposed to the list being read from the beginning. The list shows
     * this one at its end and the top bar shows [isLoading], so the two are asked separately.
     */
    val isLoadingNextPage: Boolean = false,
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

    /**
     * The list started over, so it belongs back at the top - a reload puts the new threads at index
     * 0 and the rows are keyed, so a list left part way down would otherwise hold its old anchor
     * and quietly keep the new ones off screen above it. Not sent for paging: appending a page must
     * leave the reader where they were.
     */
    data object ScrollToTop : MainListEffect
}

class MainListViewModel(
    private val threads: ZumpaThreadRepository,
    private val settings: ZumpaSettingsRepository,
    private val readStates: ZumpaReadStateRepository,
    private val selectedThread: SelectedThreadStore,
    private val eventBus: AppEventBus,
    private val windowLayout: WindowLayout,
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

    /** A reload asked for while one was already running - see [load]. */
    private var pendingReload = false
    private var openMenuId: String? = null

    init {
        viewModelScope.launch {
            windowLayout.isTwoPane.collect { fillEmptyDetailPane() }
        }
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
            //What actually turns a bar off. The count behind it is written when a thread's
            //messages arrive rather than when its row is tapped, so an opened thread stops being
            //New or Updated here - and one that could not be loaded goes on saying it has
            //something to read, which it does.
            readStates.readStates.collect { restateLoadedRows() }
        }
        viewModelScope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    //force, rather than the `lastOffline = null` this used to do. That trick got
                    //the reload to treat the offline state as changed, but it also left lastOffline
                    //null afterwards - and the isOffline collector above only reloads when
                    //lastOffline is *not* null, so one download quietly stopped the offline switch
                    //from reloading anything for the rest of the session.
                    is AppEvent.OfflineDataChanged -> load(force = true)

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

    /**
     * @param force reload from the beginning even though nothing about the query changed - the data
     * underneath did. A finished offline download is the case: same filter, same offline flag, an
     * entirely different snapshot to read.
     */
    private fun load(
        fromThread: String? = null,
        isFirstLoad: Boolean = false,
        force: Boolean = false,
    ) {
        if (state.isLoading) {
            //Asking for the next page while a load runs is nothing - it will still be there.
            //Asking for a reload is not: it is the answer to the data having changed
            //underneath, and dropping it leaves the list showing what was there before the
            //change with nothing left to trigger another try. Remembered and run afterwards.
            if (fromThread == null) {
                pendingReload = true
            }
            return
        }
        val filter = settings.filter.value
        val offline = settings.isOffline.value
        if (force || lastFilter != filter || lastOffline != offline) {
            loaded.clear()
            rowStates.clear()
        }
        lastFilter = filter
        lastOffline = offline
        setState { copy(isLoading = true, isLoadingNextPage = fromThread != null) }

        viewModelScope.launch {
            try {
                val result = threads.loadMainPage(fromThread, filter)
                nextThreadId = result.nextThreadId
                loaded.putAll(result.items)
                //the decoration is recomputed for the whole page on every load, as it was
                val userName = settings.loggedUserName.value
                result.items.values.forEach { thread ->
                    rowStates[thread.id] =
                        thread.rowStateFor(readStates.readCount(thread.id), userName)
                }
                publishRows()
                //after publishRows, so the rows the list is being sent to the top of are already
                //there. `fromThread` is the whole distinction: null is a reload of the list from
                //the beginning - pull to refresh, a new filter, the offline switch, a post landing
                //- and non-null is the next page.
                if (fromThread == null && !isFirstLoad) {
                    effect(MainListEffect.ScrollToTop)
                }
                if (isFirstLoad) {
                    fillEmptyDetailPane()
                }
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isLoading = false, isLoadingNextPage = false) }
                if (pendingReload) {
                    pendingReload = false
                    load(force = true)
                }
            }
        }
    }

    /**
     * A detail pane with nothing in it has nothing to say, so it opens on the newest thread. This is
     * what the tablet always did on its first load; a phone reaches it by being turned on its side,
     * which is why it is also collected rather than only done once - either the pane or the list can
     * be the one that arrives second.
     *
     * Not an explicit pick: it fills a pane that is there anyway, and losing the pane again must not
     * navigate to a thread nobody asked for.
     */
    private fun fillEmptyDetailPane() {
        if (!windowLayout.isTwoPane.value || selectedThread.selected.value != null) return
        threads.lastThread()?.let { selectedThread.select(it.id, explicit = false) }
    }

    /**
     * The bar is deliberately not touched here. A tap is not a read - it is a request for one, and
     * the request can fail. The read count is written when the thread's messages actually arrive,
     * and the collector above is what brings the answer back to this row.
     */
    override fun onThreadClicked(threadId: String) {
        if (threadId !in loaded) return
        openMenuId = null
        publishRows()
        if (windowLayout.isTwoPane.value) {
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
     * [stateFor] with the remembered value filled in. The rule itself is in `:shared` - the desktop
     * draws the same bar off the same read counts, and two copies of it would have drifted.
     */
    private fun ZumpaThread.rowStateFor(readCount: Int?, userName: String?): ThreadState =
        stateFor(readCount, userName, current = rowStates[id] ?: ThreadState.New)

    /**
     * The decoration for everything on screen, worked out again from the read counts - what [load]
     * does for a page it has just fetched, for the case where the counts changed underneath it
     * instead.
     */
    private fun restateLoadedRows() {
        if (loaded.isEmpty()) return
        val userName = settings.loggedUserName.value
        loaded.values.forEach { thread ->
            rowStates[thread.id] = thread.rowStateFor(readStates.readCount(thread.id), userName)
        }
        publishRows()
    }
}
