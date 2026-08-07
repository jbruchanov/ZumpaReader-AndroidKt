package com.scurab.android.zumpareader.ui.main

import android.net.Uri
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository

/**
 * The host has nothing to draw since compose C7 - every screen brings its own Scaffold - so there
 * is no state, only the routing decision for whatever the app was launched with.
 */
data object MainUiState

sealed interface MainEffect : UiEffect {
    data class OpenThread(val threadId: String) : MainEffect
    data class OpenPostDialog(
        val subject: String?,
        val text: String?,
        val uris: List<Uri>,
    ) : MainEffect
}

/**
 * What the activity was launched with, extracted from the Intent by the activity so the decision of
 * what to do with it is testable.
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
) : BaseViewModel<MainUiState>(MainUiState) {

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
