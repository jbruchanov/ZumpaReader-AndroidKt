package com.scurab.android.zumpareader.app

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The app chrome that is derived from app-wide state rather than owned by one screen.
 *
 * The toolbar title stays an imperative host call - it is per-screen text and every screen renders
 * its own with its own [com.scurab.android.zumpareader.text.ZumpaTextRenderer].
 */
data class MainUiState(
    val isProgressVisible: Boolean = false,
    val fab: FabUiState = FabUiState(),
)

data class FabUiState(
    val isVisible: Boolean = false,
    /** The QuickHideBehavior that slides the fab away while the list scrolls. */
    val isScrollHideEnabled: Boolean = true,
)

sealed interface MainEffect : UiEffect {
    data class OpenThread(val threadId: String) : MainEffect
    data class OpenPostDialog(
        val subject: String?,
        val text: String?,
        val uris: List<Uri>,
    ) : MainEffect
}

/**
 * What the activity was launched with, extracted from the Intent by the activity so the decision
 * of what to do with it is testable.
 */
data class LaunchPayload(
    val threadId: String? = null,
    val subject: String? = null,
    val text: String? = null,
    val uris: List<Uri> = emptyList(),
) {
    val isShare: Boolean get() = !(subject.isNullOrEmpty() && text.isNullOrEmpty() && uris.isEmpty())
}

class MainViewModel(
    private val settings: ZumpaSettingsRepository,
) : BaseViewModel<MainUiState>(MainUiState()) {

    /** Whether the screen currently in front has anything for the fab to do. */
    private val screenWantsFab = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            combine(settings.isLoggedInNotOffline, screenWantsFab) { canPost, wanted ->
                canPost && wanted
            }.collect { visible ->
                setState { copy(fab = fab.copy(isVisible = visible)) }
            }
        }
    }

    fun setProgressVisible(visible: Boolean) {
        setState { copy(isProgressVisible = visible) }
    }

    fun setFabWanted(wanted: Boolean) {
        screenWantsFab.value = wanted
    }

    fun setFabScrollHideEnabled(enabled: Boolean) {
        setState { copy(fab = fab.copy(isScrollHideEnabled = enabled)) }
    }

    /**
     * A push notification tap carries a thread id, a share carries a subject/text/stream. Posting
     * needs a session, so a share while logged out is a toast rather than a dialog that cannot send.
     */
    fun onLaunch(payload: LaunchPayload) {
        if (payload.threadId != null) {
            effect(MainEffect.OpenThread(payload.threadId))
            return
        }
        if (!payload.isShare) {
            return
        }
        if (!settings.isLoggedIn.value) {
            effect(ShowToast(resId = R.string.err_login_first))
            return
        }
        effect(MainEffect.OpenPostDialog(payload.subject, payload.text, payload.uris))
    }
}
