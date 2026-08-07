package com.scurab.android.zumpareader.ui.settings

import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.CopyToClipboard
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.repository.AuthRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import kotlinx.coroutines.launch

data class SettingsUiState(
    val userName: String = "",
    val password: String = "",
    val nick: String = "",
    val filter: String = "0",
    val isLoggedIn: Boolean = false,
    val isOffline: Boolean = false,
    val loadImages: Boolean = true,
    val showLastAuthor: Boolean = false,
    val areNotificationsEnabled: Boolean = false,
    val userId: String? = null,
    val isBusy: Boolean = false,
) {
    /** The filter and the "show last author" cookie only mean anything with a session. */
    val isSessionOnlyEnabled: Boolean get() = isLoggedIn && !isBusy
}

sealed interface SettingsEffect : UiEffect {
    data object RequestNotificationPermission : SettingsEffect
    data object OpenAppSettings : SettingsEffect
}

interface SettingsEventHandler {
    fun onUserNameChanged(value: String)
    fun onPasswordChanged(value: String)
    fun onNickChanged(value: String)
    fun onLoginClicked()
    fun onLogoutClicked()
    fun onFilterChanged(value: String)
    fun onLoadImagesToggled(value: Boolean)
    fun onOfflineToggled(value: Boolean)
    fun onShowLastAuthorToggled(value: Boolean)
    fun onNotificationsClicked()
    fun onUserIdClicked()
}

class SettingsViewModel(
    private val settings: ZumpaSettingsRepository,
    private val auth: AuthRepository,
    private val notifications: NotificationState,
) : BaseViewModel<SettingsUiState>(SettingsUiState()), SettingsEventHandler {

    init {
        viewModelScope.launch { settings.userName.collect { setState { copy(userName = it) } } }
        viewModelScope.launch { settings.password.collect { setState { copy(password = it) } } }
        viewModelScope.launch { settings.nick.collect { setState { copy(nick = it) } } }
        viewModelScope.launch { settings.filter.collect { setState { copy(filter = it) } } }
        viewModelScope.launch { settings.isOffline.collect { setState { copy(isOffline = it) } } }
        viewModelScope.launch { settings.loadImages.collect { setState { copy(loadImages = it) } } }
        viewModelScope.launch {
            settings.showLastAuthor.collect { setState { copy(showLastAuthor = it) } }
        }
        viewModelScope.launch {
            settings.isLoggedIn.collect {
                setState { copy(isLoggedIn = it, userId = settings.userId) }
            }
        }
    }

    /** Permission state can change outside the app, so it is re-read whenever the screen resumes. */
    fun onResumed() {
        setState { copy(areNotificationsEnabled = notifications.areEnabled()) }
    }

    override fun onUserNameChanged(value: String) = settings.setUserName(value)

    override fun onPasswordChanged(value: String) = settings.setPassword(value)

    override fun onNickChanged(value: String) = settings.setNick(value)

    override fun onFilterChanged(value: String) = settings.setFilter(value)

    override fun onLoadImagesToggled(value: Boolean) = settings.setLoadImages(value)

    override fun onOfflineToggled(value: Boolean) = settings.setOffline(value)

    override fun onShowLastAuthorToggled(value: Boolean) {
        settings.setShowLastAuthor(value)
        //it is carried as a cookie, so the jar has to be rebuilt
        auth.applyCredentials()
    }

    override fun onLoginClicked() {
        val current = state
        if (current.isBusy) return
        if (current.userName.isBlank()) {
            effect(ShowToast(resId = R.string.err_no_username))
            return
        }
        if (current.password.isBlank()) {
            effect(ShowToast(resId = R.string.err_no_password))
            return
        }

        setState { copy(isBusy = true) }
        viewModelScope.launch {
            try {
                val result = auth.login(current.userName, current.password)
                effect(ShowToast(resId = if (result.isLoggedIn) R.string.ok else R.string.err_fail))
                if (result.isLoggedIn && !result.isPushRegistered) {
                    effect(ShowToast(resId = R.string.err_no_push_reg))
                }
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isBusy = false) }
            }
        }
    }

    override fun onLogoutClicked() {
        if (state.isBusy) return
        setState { copy(isBusy = true) }
        viewModelScope.launch {
            try {
                auth.logout()
                //the "show last author" cookie is meaningless without a session
                settings.setShowLastAuthor(false)
                effect(ShowToast(resId = R.string.done))
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isBusy = false) }
            }
        }
    }

    /**
     * Below Tiramisu, and once the permission is granted, there is nothing left to ask for - the
     * only thing that can change the state then is the system settings screen.
     */
    override fun onNotificationsClicked() {
        if (state.areNotificationsEnabled || !notifications.canRequestPermission()) {
            effect(SettingsEffect.OpenAppSettings)
        } else {
            effect(SettingsEffect.RequestNotificationPermission)
        }
    }

    override fun onUserIdClicked() {
        state.userId?.let {
            effect(CopyToClipboard(it))
        }
    }
}

/**
 * The notification bits the ViewModel needs, without an android import in it.
 */
interface NotificationState {
    fun areEnabled(): Boolean
    fun canRequestPermission(): Boolean
}
