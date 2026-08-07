package com.scurab.android.zumpareader.ui.sublist

import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.CopyToClipboard
import com.scurab.android.zumpareader.arch.DeviceConfig
import com.scurab.android.zumpareader.arch.HideKeyboard
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaVoteSurveyBody
import com.scurab.android.zumpareader.repository.AppEvent
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.SelectedThreadStore
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.looksLikeImageUrl
import kotlinx.coroutines.launch

/**
 * One row of the thread. The adapter used to derive these itself in `buildAdapterItems`; deciding
 * that a message with three urls is four rows is list shaping, not view work, so it lives here and
 * the adapter is left with getItemViewType and bind. It is also what makes the later LazyColumn a
 * direct translation.
 */
sealed interface SubListRowUiState {
    val itemIndex: Int

    data class Message(
        override val itemIndex: Int,
        val author: String,
        val authorReal: String?,
        val rating: String?,
        /** Raw markup - rendered by [com.scurab.android.zumpareader.text.ZumpaTextRenderer]. */
        val body: String,
        val time: Long,
        /** The swipe-to-reveal reply/copy/quote menu. */
        val isMenuOpen: Boolean = false,
    ) : SubListRowUiState

    data class Link(override val itemIndex: Int, val url: String) : SubListRowUiState

    data class Image(override val itemIndex: Int, val url: String) : SubListRowUiState

    data class Survey(
        override val itemIndex: Int,
        val survey: SurveyUiState,
    ) : SubListRowUiState
}

data class SurveyUiState(
    val id: String,
    val question: String,
    val responses: Int,
    val items: List<SurveyItemUiState>,
)

data class SurveyItemUiState(
    val id: Int,
    val surveyId: String,
    val text: String,
    val percents: Int,
    val voted: Boolean,
)

/**
 * The reply box.
 *
 * The old implementation kept the inserted `@author: ` headers in the Editable and found them again
 * through AuthorSpan - spans used as data rather than as styling, which is why tapping a message
 * twice removed its header. [headers] is that data, and the colour becomes a render concern.
 */
data class DraftUiState(
    val headers: List<String> = emptyList(),
    val body: String = "",
) {
    val text: String get() = headers.joinToString(separator = "") + body

    val isBlank: Boolean get() = text.isBlank()
}

data class SubListUiState(
    val threadId: String = "",
    /** Raw subject markup for the toolbar. */
    val title: String = "",
    val rows: List<SubListRowUiState> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isPostPanelVisible: Boolean = false,
    val draft: DraftUiState = DraftUiState(),
    val canPost: Boolean = false,
)

interface SubListEventHandler {
    fun onRefreshRequested()
    fun onMessageClicked(row: SubListRowUiState.Message)
    fun onMessageLongPressed(row: SubListRowUiState.Message)
    fun onReplyClicked(authorReal: String?)
    fun onQuoteClicked(author: String, body: String)
    fun onCopyClicked(body: String)
    fun onDraftChanged(text: String)
    fun onSendClicked()
    fun onSurveyItemClicked(item: SurveyItemUiState)
    fun onLinkClicked(url: String)
    fun onImageClicked(url: String)
    fun onPostPanelRequested()
    fun onPostPanelDismissed()
    fun onMenuToggled(itemIndex: Int)
}

sealed interface SubListEffect : UiEffect {
    data object ScrollToBottom : SubListEffect
    data object ScrollToTop : SubListEffect
    /** Phone only - on a tablet a thread link swaps the pane through [SelectedThreadStore]. */
    data class OpenThread(val threadId: String) : SubListEffect
    data object OpenPostFragment : SubListEffect
    data class OpenImage(val url: String) : SubListEffect
    data class OpenLink(val url: String) : SubListEffect
}

class SubListViewModel(
    private val threads: ZumpaThreadRepository,
    private val settings: ZumpaSettingsRepository,
    private val readStates: ZumpaReadStateRepository,
    private val selectedThread: SelectedThreadStore,
    private val eventBus: AppEventBus,
    private val device: DeviceConfig,
) : BaseViewModel<SubListUiState>(SubListUiState()), SubListEventHandler {

    private var items: List<ZumpaThreadItem> = emptyList()
    private var isStarted = false
    private var openMenuIndex: Int? = null

    init {
        viewModelScope.launch {
            settings.isLoggedInNotOffline.collect { canPost ->
                setState { copy(canPost = canPost) }
            }
        }
        viewModelScope.launch {
            settings.loadImages.collect { publishRows() }
        }
        viewModelScope.launch {
            eventBus.events.collect { event ->
                if (event is AppEvent.ContentPosted) {
                    reload()
                }
            }
        }
        viewModelScope.launch {
            //on a tablet this is how the list pane hands a thread over
            selectedThread.selected.collect { threadId ->
                if (threadId != null && threadId != state.threadId) {
                    openThread(threadId)
                }
            }
        }
    }

    /** The id the fragment was created with. Ignored once a selection has taken over. */
    fun start(threadId: String) {
        if (isStarted) return
        isStarted = true
        if (state.threadId.isEmpty() && threadId.isNotEmpty()) {
            openThread(threadId)
        }
    }

    private fun openThread(threadId: String) {
        val isSwitch = state.threadId.isNotEmpty() && state.threadId != threadId
        items = emptyList()
        setState {
            copy(
                threadId = threadId,
                title = threads.thread(threadId)?.subject ?: "",
                rows = emptyList(),
                //selecting a thread on a tablet is what reveals the reply box there
                isPostPanelVisible = if (device.isTablet) true else isPostPanelVisible,
            )
        }
        load(scrollToTop = isSwitch)
    }

    override fun onRefreshRequested() = load()

    fun reload() = load(scrollToBottom = true)

    private fun load(scrollToTop: Boolean = false, scrollToBottom: Boolean = false) {
        val threadId = state.threadId
        if (threadId.isEmpty() || state.isLoading) {
            return
        }
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            try {
                items = threads.loadThread(threadId)
                //don't count the opening post, it is not an answer
                readStates.markRead(threadId, items.size - 1)
                setState { copy(title = threads.thread(threadId)?.subject ?: title) }
                publishRows()
                when {
                    scrollToBottom -> effect(SubListEffect.ScrollToBottom)
                    scrollToTop -> effect(SubListEffect.ScrollToTop)
                }
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isLoading = false, isSending = false) }
            }
        }
    }

    //region the reply box
    override fun onDraftChanged(text: String) {
        if (text == state.draft.text) return
        setState { copy(draft = draft.reparse(text)) }
    }

    /** Tapping a message inserts its author, tapping it again takes the header back out. */
    override fun onReplyClicked(authorReal: String?) {
        if (!state.canPost) return
        closeMenu()
        val header = REPLY_HEADER.format(authorReal ?: return)
        showPostPanel()
        setState {
            copy(
                draft = if (header in draft.headers) {
                    draft.copy(headers = draft.headers - header)
                } else {
                    draft.copy(headers = draft.headers + header)
                }
            )
        }
    }

    /** The "speak" menu item - quote the whole message at the end of the draft. */
    override fun onQuoteClicked(author: String, body: String) {
        if (!state.canPost) return
        closeMenu()
        showPostPanel()
        setState {
            val separator = if (draft.body.isNotEmpty()) "\n" else ""
            copy(draft = draft.copy(body = "${draft.body}$separator$author: $body\n----\n"))
        }
    }

    override fun onCopyClicked(body: String) {
        closeMenu()
        effect(CopyToClipboard(body))
    }

    /** Tapping a message with the reply box open inserts its author, as it did on a phone. */
    override fun onMessageClicked(row: SubListRowUiState.Message) {
        if (state.canPost && state.isPostPanelVisible) {
            onReplyClicked(row.authorReal)
        }
    }

    override fun onMessageLongPressed(row: SubListRowUiState.Message) = onMenuToggled(row.itemIndex)

    override fun onMenuToggled(itemIndex: Int) {
        if (!state.canPost) return
        openMenuIndex = if (openMenuIndex == itemIndex) null else itemIndex
        publishRows()
    }

    private fun closeMenu() {
        if (openMenuIndex != null) {
            openMenuIndex = null
            publishRows()
        }
    }

    /**
     * Link routing. It lived in the fragment while the image viewer needed the tapped view for a
     * shared element transition; that transition is gone (UPGRADE_PLAN E1), so the decision is just
     * logic now.
     */
    override fun onLinkClicked(url: String) {
        val threadId = ZumpaSimpleParser.getZumpaThreadId(url)
        when {
            threadId != 0 -> onThreadLinkClicked(threadId.toString())
            url.looksLikeImageUrl() -> effect(SubListEffect.OpenImage(url))
            else -> effect(SubListEffect.OpenLink(url))
        }
    }

    override fun onImageClicked(url: String) = effect(SubListEffect.OpenImage(url))

    override fun onPostPanelRequested() = showPostPanel()

    override fun onPostPanelDismissed() {
        onBackPressed()
    }

    fun showPostPanel() {
        if (!state.isPostPanelVisible) {
            setState { copy(isPostPanelVisible = true) }
        }
    }

    /** True when it had something to close, which is what the back gesture consumes. */
    fun onBackPressed(): Boolean {
        if (state.canPost && state.isPostPanelVisible && !device.isTablet) {
            setState { copy(isPostPanelVisible = false) }
            return true
        }
        return false
    }

    override fun onSendClicked() {
        val draft = state.draft
        if (draft.isBlank || state.isSending) {
            return
        }
        val threadId = state.threadId
        val subject = threads.thread(threadId)?.subject ?: ""
        val body = ZumpaThreadBody(settings.nickName, subject, draft.text, threadId)
        setState { copy(isSending = true) }
        effect(HideKeyboard)
        viewModelScope.launch {
            try {
                threads.sendResponse(threadId, body)
                setState { copy(draft = DraftUiState(), isPostPanelVisible = false) }
                load(scrollToBottom = true)
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isSending = false) }
            }
        }
    }
    //endregion

    override fun onSurveyItemClicked(item: SurveyItemUiState) {
        if (!state.canPost) return
        setState { copy(isSending = true) }
        viewModelScope.launch {
            try {
                threads.voteSurvey(ZumpaVoteSurveyBody(item.surveyId, item.id))
                load()
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isSending = false) }
            }
        }
    }

    fun onThreadLinkClicked(threadId: String) {
        if (device.isTablet) {
            selectedThread.select(threadId)
        } else {
            effect(SubListEffect.OpenThread(threadId))
        }
    }

    fun onOpenPostFragment() = effect(SubListEffect.OpenPostFragment)

    private fun publishRows() {
        val loadImages = settings.loadImages.value
        val rows = ArrayList<SubListRowUiState>((items.size * ROW_GROWTH).toInt())
        items.forEachIndexed { index, item ->
            rows += SubListRowUiState.Message(
                itemIndex = index,
                author = item.author,
                authorReal = item.authorReal,
                rating = item.rating,
                body = item.body,
                time = item.time,
                isMenuOpen = index == openMenuIndex,
            )
            item.urls?.let { urls ->
                //images before plain links, which is what the old sortBy(type) did
                val (images, links) = urls.partition { it.looksLikeImageUrl() && loadImages }
                images.forEach { rows += SubListRowUiState.Image(index, it) }
                links.forEach { rows += SubListRowUiState.Link(index, it) }
            }
            if (index == 0) {
                item.survey?.let { survey ->
                    rows += SubListRowUiState.Survey(
                        itemIndex = index,
                        survey = SurveyUiState(
                            id = survey.id,
                            question = survey.question,
                            responses = survey.responses,
                            items = survey.items.map {
                                SurveyItemUiState(it.id, it.surveyId, it.text, it.percents, it.voted)
                            },
                        ),
                    )
                }
            }
        }
        setState { copy(rows = rows) }
    }

    private companion object {
        const val REPLY_HEADER = "@%s: \n"
        const val ROW_GROWTH = 1.3
    }
}

/**
 * Splits an edited draft back into the reply headers and the body. Headers the user has deleted
 * stop being headers, everything else is body.
 */
private fun DraftUiState.reparse(text: String): DraftUiState {
    val kept = ArrayList<String>(headers.size)
    var rest = text
    for (header in headers) {
        if (rest.startsWith(header)) {
            kept += header
            rest = rest.removePrefix(header)
        }
    }
    return DraftUiState(headers = kept, body = rest)
}
