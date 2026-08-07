package com.scurab.android.zumpareader.ui.post

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.HideKeyboard
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.AppEvent
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import kotlinx.coroutines.launch

const val POST_MESSAGE_TAB = "1"

sealed interface PostTabUiState {
    val tag: String

    data object Message : PostTabUiState {
        override val tag: String get() = POST_MESSAGE_TAB
    }

    data class Image(
        override val tag: String,
        val uri: Uri,
        @DrawableRes val iconRes: Int,
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
) {
    val canSend: Boolean
        get() = message.isNotBlank() && (!isSubjectEditable || subject.isNotBlank())
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
) : BaseViewModel<PostUiState>(PostUiState()) {

    private var threadId: String? = null

    /**
     * The `argFlag` opens the camera or the gallery straight away, once. The old implementation
     * carried a `//TODO: doesn't work with lifecycle!, has to be saved` next to this flag and hand
     * rolled it through onSaveInstanceState; here it simply lives as long as the dialog does.
     */
    private var isFlagConsumed = false

    private var isStarted = false

    fun start(args: PostArgs) {
        if (isStarted) return
        isStarted = true
        threadId = args.threadId
        setState {
            copy(
                subject = args.subject.orEmpty(),
                //bare urls in a shared message become zumpa links
                message = ZumpaSimpleParser.replaceLinksByZumpaLinks(args.message).orEmpty(),
                isSubjectEditable = args.threadId == null,
                tabs = buildList {
                    add(PostTabUiState.Message)
                    args.uris.forEachIndexed { index, uri ->
                        add(PostTabUiState.Image("${index + 2}", uri, R.drawable.ic_photo))
                    }
                },
                //a single shared image goes straight to its tab
                selectedTabTag = if (args.uris.size == 1) "2" else null,
            )
        }
    }

    /** Consumed once, so rotating the dialog does not reopen the picker. */
    fun onFlag(flag: Int) {
        if (isFlagConsumed || flag == 0) return
        isFlagConsumed = true
        when (flag) {
            R.id.photo -> effect(PostEffect.RequestGalleryImage)
            R.id.camera -> effect(PostEffect.RequestCameraImage)
        }
    }

    fun onCameraClick() = effect(PostEffect.RequestCameraImage)

    fun onPhotoClick() = effect(PostEffect.RequestGalleryImage)

    fun onImagePicked(uri: Uri, fromCamera: Boolean) {
        val tag = "${state.tabs.size + 1} - $uri"
        setState {
            copy(
                tabs = tabs + PostTabUiState.Image(
                    tag = tag,
                    uri = uri,
                    iconRes = if (fromCamera) R.drawable.ic_camera else R.drawable.ic_photo,
                ),
                selectedTabTag = tag,
            )
        }
    }

    /**
     * A giphy pick and a finished upload are the same thing to the message: a zumpa link on its own
     * line. The old code held the giphy result in a field applied in onResume, and lost it if the
     * process died.
     */
    fun onLinkShared(link: String) {
        setState {
            val separator = if (message.isEmpty() || message.endsWith("\n")) "" else "\n"
            copy(
                message = "$message$separator<$link>\n",
                selectedTabTag = POST_MESSAGE_TAB,
            )
        }
    }

    fun onTabSelected(tag: String) {
        if (state.selectedTabTag != tag) {
            setState { copy(selectedTabTag = tag) }
        }
    }

    fun onSubjectChanged(subject: String) {
        if (subject != state.subject) setState { copy(subject = subject) }
    }

    fun onMessageChanged(message: String) {
        if (message != state.message) setState { copy(message = message) }
    }

    fun onSend() {
        val current = state
        if (current.isSending) return
        if (current.isSubjectEditable && current.subject.isBlank()) {
            effect(ShowToast(resId = R.string.err_empty_subject))
            return
        }
        if (current.message.isBlank()) {
            effect(ShowToast(resId = R.string.err_empty_msg))
            return
        }

        setState { copy(isSending = true) }
        effect(HideKeyboard)
        val id = threadId
        viewModelScope.launch {
            try {
                if (id == null) {
                    threads.sendThread(
                        ZumpaThreadBody(settings.nickName, current.subject.trim(), current.message.trim())
                    )
                } else {
                    val subject = threads.thread(id)?.subject ?: current.subject
                    threads.sendResponse(
                        id,
                        ZumpaThreadBody(settings.nickName, subject, current.message.trim(), id)
                    )
                }
                eventBus.emit(AppEvent.ContentPosted)
                effect(PostEffect.Dismiss)
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isSending = false) }
            }
        }
    }
}

/** What [PostViewModel] needs out of the fragment arguments. */
data class PostArgs(
    val subject: String? = null,
    val message: String? = null,
    val uris: List<Uri> = emptyList(),
    val threadId: String? = null,
)
