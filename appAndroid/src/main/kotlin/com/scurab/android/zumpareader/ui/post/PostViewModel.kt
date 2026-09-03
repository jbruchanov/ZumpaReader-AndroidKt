package com.scurab.android.zumpareader.ui.post

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.HideKeyboard
import com.scurab.android.zumpareader.arch.ShowSnackbar
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.AppEvent
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.Draft
import com.scurab.android.zumpareader.repository.RestoreMode
import com.scurab.android.zumpareader.repository.SentDraft
import com.scurab.android.zumpareader.repository.SentDraftRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.repository.restoredInto
import com.scurab.android.zumpareader.ui.compose.RestoreDraftEventHandler
import kotlinx.coroutines.launch

const val POST_MESSAGE_TAB = "1"

sealed interface PostTabUiState {
    val tag: String

    data object Message : PostTabUiState {
        override val tag: String get() = POST_MESSAGE_TAB
    }

    /**
     * [fromCamera] rather than an icon: which glyph that becomes is the screen`s business, and a
     * ViewModel has no reason to hold a drawable id - see `PostTabUiState.icon()` in PostScreen.
     */
    data class Image(
        override val tag: String,
        val uri: Uri,
        val fromCamera: Boolean,
    ) : PostTabUiState
}

/**
 * Shared by the dialog and both of its tabs - the message tab binds to this rather than owning a
 * ViewModel of its own, which is what replaces the `(parentFragment as PostFragment).onSharedImage`
 * up-calls between the two children.
 */
data class PostUiState(
    val tabs: List<PostTabUiState> = listOf(PostTabUiState.Message),
    val selectedTabTag: String? = null,
    val subject: String = "",
    val message: String = "",
    /** False when replying into an existing thread - the subject is fixed then. */
    val isSubjectEditable: Boolean = true,
    val isSending: Boolean = false,
    /** The last thing sent, if anything has been - what the restore button offers. */
    val sentDraft: SentDraft? = null,
    /** Non-null while the restore dialog is up, which only happens over a field with text in it. */
    val restorePrompt: SentDraft? = null,
) {
    val canSend: Boolean
        get() = message.isNotBlank() && (!isSubjectEditable || subject.isNotBlank())
}

/**
 * The dialog's own interactions. Extends the message tab's, so the single event handler [PostScreen]
 * is given also satisfies the message page it renders.
 */
interface PostEventHandler : PostMessageEventHandler {
    fun onTabSelected(tag: String)

    /** An image tab finished uploading; the link belongs in the message draft. */
    fun onImageLinkUploaded(link: String)
}

/** The message tab's interactions. The image tabs have their own, see [PostImageEventHandler]. */
interface PostMessageEventHandler : RestoreDraftEventHandler {
    fun onSubjectChanged(subject: String)
    fun onMessageChanged(message: String)
    fun onSendClicked()
    fun onCameraClicked()
    fun onPhotoClicked()
}

sealed interface PostEffect : UiEffect {
    data object RequestCameraImage : PostEffect
    data object RequestGalleryImage : PostEffect
    data object Dismiss : PostEffect
}

class PostViewModel(
    private val threads: ZumpaThreadRepository,
    private val settings: ZumpaSettingsRepository,
    private val eventBus: AppEventBus,
    private val sentDrafts: SentDraftRepository,
) : BaseViewModel<PostUiState>(PostUiState()), PostEventHandler {

    init {
        //collected rather than read once: sending from the thread's reply panel while this dialog
        //is open is exactly the case the button is for
        viewModelScope.launch {
            sentDrafts.draft.collect { setState { copy(sentDraft = it) } }
        }
    }

    private var threadId: String? = null

    /**
     * The `argFlag` opens the camera or the gallery straight away, once. The old implementation
     * carried a `//TODO: doesn't work with lifecycle!, has to be saved` next to this flag and hand
     * rolled it through onSaveInstanceState - and that TODO was right. This one only covers a
     * single ViewModel lifetime; PostScreen holds the durable half in saved state, because the
     * picker it guards is restored off the back stack after the process is killed.
     */
    private var isFlagConsumed = false

    private var isStarted = false

    /** Pictures that arrived as arguments - a share - as opposed to ones picked in the screen. */
    private var sharedUris: List<Uri> = emptyList()

    fun start(args: PostArgs) {
        if (isStarted) return
        isStarted = true
        threadId = args.threadId
        sharedUris = args.uris
        setState {
            copy(
                subject = args.subject.orEmpty(),
                //bare urls in a shared message become zumpa links
                message = ZumpaSimpleParser.replaceLinksByZumpaLinks(args.message).orEmpty(),
                isSubjectEditable = args.threadId == null,
                tabs = tabsFor(picks = emptyList()),
                //a single shared image goes straight to its tab
                selectedTabTag = if (args.uris.size == 1) sharedTag(0) else null,
            )
        }
    }

    /**
     * The pictures the screen is holding, in the order they were picked.
     *
     * Handed over whole rather than one at a time, because the screen is what remembers them across
     * a recreation and this cannot: a ViewModel that came back empty used to have [start] rebuild
     * the tabs from the arguments alone, throwing away every picture picked before it. So this is
     * told the lot and rebuilds from the lot - the same list twice is the same tabs twice.
     *
     * Tags are positional and stable for that reason too. They used to include the tab count and
     * the uri, so the same picture came back under a different tag after a recreation and the
     * per-tab upload ViewModel keyed on it started again from nothing.
     */
    fun applyPicks(picks: List<PickedImage>) {
        val rebuilt = tabsFor(picks)
        val isNew = rebuilt.size > state.tabs.size
        setState {
            copy(
                tabs = rebuilt,
                //a picture just added is the one to be looking at; otherwise leave the choice alone
                selectedTabTag = if (isNew) rebuilt.last().tag else selectedTabTag,
            )
        }
    }

    private fun tabsFor(picks: List<PickedImage>): List<PostTabUiState> = buildList {
        add(PostTabUiState.Message)
        sharedUris.forEachIndexed { index, uri ->
            add(PostTabUiState.Image(sharedTag(index), uri, fromCamera = false))
        }
        picks.forEachIndexed { index, pick ->
            add(PostTabUiState.Image(pickTag(index), pick.uri, pick.fromCamera))
        }
    }

    /** Consumed once, so rotating the dialog does not reopen the picker. */
    fun onPicker(picker: PostPicker?) {
        if (isFlagConsumed || picker == null) return
        isFlagConsumed = true
        when (picker) {
            PostPicker.Gallery -> effect(PostEffect.RequestGalleryImage)
            PostPicker.Camera -> effect(PostEffect.RequestCameraImage)
        }
    }

    override fun onCameraClicked() = effect(PostEffect.RequestCameraImage)

    override fun onPhotoClicked() = effect(PostEffect.RequestGalleryImage)



    /**
     * A giphy pick and a finished upload are the same thing to the message: a zumpa link on its own
     * line. The old code held the giphy result in a field applied in onResume, and lost it if the
     * process died.
     */
    override fun onImageLinkUploaded(link: String) = onLinkShared(link)

    fun onLinkShared(link: String) {
        setState {
            val separator = if (message.isEmpty() || message.endsWith("\n")) "" else "\n"
            copy(
                message = "$message$separator<$link>\n",
                selectedTabTag = POST_MESSAGE_TAB,
            )
        }
    }

    override fun onTabSelected(tag: String) {
        if (state.selectedTabTag != tag) {
            setState { copy(selectedTabTag = tag) }
        }
    }

    override fun onSubjectChanged(subject: String) {
        if (subject != state.subject) setState { copy(subject = subject) }
    }

    override fun onMessageChanged(message: String) {
        if (message != state.message) setState { copy(message = message) }
    }

    override fun onSendClicked() {
        val current = state
        if (current.isSending) return
        if (current.isSubjectEditable && current.subject.isBlank()) {
            effect(ShowSnackbar(resId = R.string.err_empty_subject))
            return
        }
        if (current.message.isBlank()) {
            effect(ShowSnackbar(resId = R.string.err_empty_msg))
            return
        }

        //before the call, not after it: the whole point is that the answer cannot be trusted, and a
        //draft saved only on success would be missing for exactly the posts this is here for.
        //Also before the link-wrapping below: a restored draft should be what the writer typed, not
        //what the forum's link syntax rewrote it into.
        sentDrafts.save(
            message = current.message.trim(),
            subject = current.subject.trim().takeIf { current.isSubjectEditable },
        )

        //the forum only makes a link clickable when it sits inside `<>`, so every url in the
        //message is wrapped on the way out
        val messageToSend = ZumpaSimpleParser.replaceLinksByZumpaLinks(current.message.trim())
            .orEmpty()

        setState { copy(isSending = true) }
        effect(HideKeyboard)
        val id = threadId
        viewModelScope.launch {
            try {
                if (id == null) {
                    threads.sendThread(
                        ZumpaThreadBody(settings.nickName, current.subject.trim(), messageToSend)
                    )
                } else {
                    val subject = threads.thread(id)?.subject ?: current.subject
                    threads.sendResponse(
                        id,
                        ZumpaThreadBody(settings.nickName, subject, messageToSend, id)
                    )
                }
                //the thread it went into, so the screen showing that thread knows it was this post
                eventBus.emit(AppEvent.ContentPosted(id))
                effect(PostEffect.Dismiss)
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isSending = false) }
            }
        }
    }

    //region restoring the last sent message - see RestoreDraftEventHandler
    /**
     * A blank field has nothing to lose, so it is filled without asking. Anything already written
     * gets the dialog, which is the only place the append/overwrite choice exists.
     */
    override fun onRestoreDraftClicked() {
        val saved = state.sentDraft ?: return
        if (state.message.isBlank()) {
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
        val current = state
        //null subject means "not this writer's to set", which is what tells the restore rules this
        //is a reply and a saved subject has to be dropped
        val restored = saved.restoredInto(
            current = Draft(
                message = current.message,
                subject = current.subject.takeIf { current.isSubjectEditable },
            ),
            mode = mode,
        )
        setState {
            copy(
                message = restored.message,
                subject = restored.subject ?: subject,
                //the message tab is where the restored text landed, so that is where to look
                selectedTabTag = POST_MESSAGE_TAB,
            )
        }
    }
    //endregion
}

/**
 * Opening the dialog straight into a picker. Used to be a `R.id.camera` / `R.id.photo` int, i.e. a
 * View id used as an enum, which stopped compiling the moment the layout holding those ids was
 * deleted.
 */
enum class PostPicker { Camera, Gallery }

/** What [PostViewModel] needs out of the fragment arguments. */
data class PostArgs(
    val subject: String? = null,
    val message: String? = null,
    val uris: List<Uri> = emptyList(),
    val threadId: String? = null,
)

/** One picture the reader chose, and where it came from. */
data class PickedImage(val uri: Uri, val fromCamera: Boolean)

private fun sharedTag(index: Int) = "shared-$index"

private fun pickTag(index: Int) = "picked-$index"
