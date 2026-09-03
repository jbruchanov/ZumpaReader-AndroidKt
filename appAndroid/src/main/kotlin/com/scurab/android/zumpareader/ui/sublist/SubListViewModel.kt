package com.scurab.android.zumpareader.ui.sublist

import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.CopyToClipboard
import com.scurab.android.zumpareader.arch.HideKeyboard
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.arch.WindowLayout
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaVoteSurveyBody
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.AppEvent
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.Draft
import com.scurab.android.zumpareader.repository.RestoreMode
import com.scurab.android.zumpareader.repository.SelectedThreadStore
import com.scurab.android.zumpareader.repository.SentDraft
import com.scurab.android.zumpareader.repository.SentDraftRepository
import com.scurab.android.zumpareader.repository.restoredInto
import com.scurab.android.zumpareader.ui.compose.RestoreDraftEventHandler
import com.scurab.android.zumpareader.ui.post.PostPicker
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
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

    /**
     * @param isLastInGroup the row is the last one belonging to its message. A message's link
     * rows use a tight vertical padding so consecutive buttons cluster together, but the tail one
     * has to close the card off with the same 8dp gap a plain message ends with.
     */
    data class Link(
        override val itemIndex: Int,
        val url: String,
        val isLastInGroup: Boolean = false,
    ) : SubListRowUiState

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
    /** The last thing sent, if anything has been - what the restore button offers. */
    val sentDraft: SentDraft? = null,
    /** Non-null while the restore dialog is up, which only happens over a field with text in it. */
    val restorePrompt: SentDraft? = null,
)

interface SubListEventHandler : RestoreDraftEventHandler {
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
    fun onLinkLongPressed(url: String)
    fun onImageClicked(url: String)
    fun onPostPanelRequested()
    fun onPostPanelDismissed()
    fun onReplyPhotoClicked()
    fun onReplyCameraClicked()
    fun onMenuToggled(itemIndex: Int)
}

sealed interface SubListEffect : UiEffect {
    /**
     * @param index the row to end on. Carried rather than worked out in the screen, because the
     * only thing that knows how many rows there are the moment they are published is whatever
     * published them. Reading the list's own `totalItemsCount` when the effect arrives gives the
     * count from before the reload - the list lays out on a later frame - which is how a reply that
     * had just been sent ended up one row off the bottom.
     */
    data class ScrollToBottom(val index: Int) : SubListEffect
    data object ScrollToTop : SubListEffect
    /** Phone only - on a tablet a thread link swaps the pane through [SelectedThreadStore]. */
    data class OpenThread(val threadId: String) : SubListEffect
    /**
     * The post screen, for this thread. [picker] opens straight into the gallery or the camera - the
     * reply panel`s two image buttons, which did `onOpenPostFragment(R.id.photo)` before.
     */
    data class OpenPostDialog(
        val threadId: String,
        val picker: PostPicker? = null,
    ) : SubListEffect
    data class OpenImage(val url: String) : SubListEffect
    data class OpenLink(val url: String) : SubListEffect
}

class SubListViewModel(
    private val threads: ZumpaThreadRepository,
    private val settings: ZumpaSettingsRepository,
    private val readStates: ZumpaReadStateRepository,
    private val selectedThread: SelectedThreadStore,
    private val eventBus: AppEventBus,
    private val windowLayout: WindowLayout,
    private val sentDrafts: SentDraftRepository,
) : BaseViewModel<SubListUiState>(SubListUiState()), SubListEventHandler {

    private var items: List<ZumpaThreadItem> = emptyList()
    private var isStarted = false
    private var openMenuIndex: Int? = null

    init {
        viewModelScope.launch {
            sentDrafts.draft.collect { setState { copy(sentDraft = it) } }
        }
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
                    //the full-screen writer opened from this panel's photo or camera button, and
                    //the post went through over there. What is still in the panel has been said, so
                    //it goes the same way it would have if send had been pressed here.
                    //
                    //Only for this thread: a new thread posted from the list is somebody else's
                    //business, and clearing on that would throw away a draft over an unrelated post.
                    if (event.threadId == state.threadId) {
                        clearDraft()
                    }
                    reload()
                }
            }
        }
        viewModelScope.launch {
            //with two panes this is how the list pane hands a thread over. With one it is only
            //a memory of what was open, for the next rotation, and must not steer this screen -
            //which is showing whatever thread it was navigated to.
            selectedThread.selected.collect { threadId ->
                if (windowLayout.isTwoPane.value && threadId != null && threadId != state.threadId) {
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
                //selecting a thread in the detail pane is what reveals the reply box there
                isPostPanelVisible = if (windowLayout.isTwoPane.value) true else isPostPanelVisible,
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
                //here, and not where the row was tapped: a thread that did not load has not been
                //read, and the list behind this should go on saying so
                readStates.markRead(threadId, items)
                setState { copy(title = threads.thread(threadId)?.subject ?: title) }
                publishRows()
                when {
                    scrollToBottom ->
                        effect(SubListEffect.ScrollToBottom(maxOf(0, state.rows.lastIndex)))
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
     *
     * A `www.` link is stored without a scheme, and `ACTION_VIEW` on a schemeless uri does not open
     * a browser, so it is completed with `http://` at the point it leaves for one.
     */
    override fun onLinkClicked(url: String) {
        val threadId = ZumpaSimpleParser.getZumpaThreadId(url)
        val fullUrl = if (url.startsWith("www.", ignoreCase = true)) "http://$url" else url
        when {
            threadId != 0 -> onThreadLinkClicked(threadId.toString())
            fullUrl.looksLikeImageUrl() -> effect(SubListEffect.OpenImage(fullUrl))
            else -> effect(SubListEffect.OpenLink(fullUrl))
        }
    }

    /**
     * Holding a link or a picture copies its address, which is what `onItemClick(url, longClick)`
     * did before the compose migration. The system shows its own confirmation for a clipboard write
     * from Android 13 on, so the app no longer says anything of its own.
     */
    override fun onLinkLongPressed(url: String) = effect(CopyToClipboard(url))

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

    /**
     * What a sent reply leaves behind: nothing in the box, and the box put away.
     *
     * Shared by the two ways a reply reaches the forum - pressing send here, and sending from the
     * full-screen writer this panel's image buttons open - so the two cannot drift.
     */
    private fun clearDraft() {
        setState { copy(draft = DraftUiState(), isPostPanelVisible = false) }
    }

    /** True when it had something to close, which is what the back gesture consumes. */
    fun onBackPressed(): Boolean {
        if (state.canPost && state.isPostPanelVisible && !windowLayout.isTwoPane.value) {
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
        //before the call: the forum sometimes accepts a reply, says so and does nothing with it,
        //so a draft kept only on success would be missing for the posts this exists for. A reply
        //carries no subject of its own - the thread owns it. Also before the link-wrapping below,
        //because a restored draft is meant to be the writer's own text - not the `<>`-decorated
        //rewrite the forum needs to make a url clickable.
        sentDrafts.save(message = draft.text, subject = null)
        //the forum only makes a link clickable when it sits inside `<>`, so every url in the
        //message is wrapped on the way out - see PostViewModel.onSendClicked for the other path
        val messageToSend = ZumpaSimpleParser.replaceLinksByZumpaLinks(draft.text).orEmpty()
        val body = ZumpaThreadBody(settings.nickName, subject, messageToSend, threadId)
        setState { copy(isSending = true) }
        effect(HideKeyboard)
        viewModelScope.launch {
            try {
                threads.sendResponse(threadId, body)
                clearDraft()
                load(scrollToBottom = true)
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isSending = false) }
            }
        }
    }

    //region restoring the last sent message - see RestoreDraftEventHandler
    /**
     * A blank field is filled without asking; anything already written gets the dialog.
     *
     * The restored text lands in the draft's [DraftUiState.body], not over its headers: the
     * `@author:` prefixes are the reply's addressing, and a message being written again is still
     * addressed to whoever it was addressed to.
     */
    override fun onRestoreDraftClicked() {
        val saved = state.sentDraft ?: return
        if (state.draft.body.isBlank()) {
            restore(saved, RestoreMode.Fill)
        } else {
            setState { copy(restorePrompt = saved) }
        }
    }

    override fun onRestoreDraftDismissed() = setState { copy(restorePrompt = null) }

    override fun onRestoreDraftAppended() = answerPrompt(RestoreMode.Append)

    override fun onRestoreDraftOverwritten() = answerPrompt(RestoreMode.Overwrite)

    private fun answerPrompt(mode: RestoreMode) {
        val saved = state.restorePrompt ?: return
        restore(saved, mode)
        setState { copy(restorePrompt = null) }
    }

    private fun restore(saved: SentDraft, mode: RestoreMode) {
        //a null subject says "a reply", which is what makes the rules drop a saved one
        val restored = saved.restoredInto(Draft(state.draft.body, subject = null), mode)
        setState { copy(draft = draft.copy(body = restored.message)) }
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
        if (windowLayout.isTwoPane.value) {
            selectedThread.select(threadId)
        } else {
            effect(SubListEffect.OpenThread(threadId))
        }
    }

    override fun onReplyPhotoClicked() =
        effect(SubListEffect.OpenPostDialog(state.threadId, PostPicker.Gallery))

    override fun onReplyCameraClicked() =
        effect(SubListEffect.OpenPostDialog(state.threadId, PostPicker.Camera))

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
                //the last link closes the card; the row uses that to widen its bottom padding
                links.forEachIndexed { i, url ->
                    rows += SubListRowUiState.Link(index, url, isLastInGroup = i == links.lastIndex)
                }
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
