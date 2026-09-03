package com.scurab.android.zumpareader.arch

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * A one-shot thing the view has to do - navigate, show a snackbar, scroll, touch the clipboard. Anything that
 * must not be replayed when the state is re-collected after a configuration change belongs here and
 * not in the ui state.
 */
interface UiEffect

data class ShowSnackbar(val text: String? = null, @StringRes val resId: Int = 0) : UiEffect

data object HideKeyboard : UiEffect

data class CopyToClipboard(val text: CharSequence) : UiEffect

/**
 * Holds the whole state of one screen in a single immutable [S] and exposes it as a [StateFlow].
 * The view layer subscribes to [uiState] and renders it, it never reads state back out of widgets.
 *
 * Nothing in [S] may be mutable, or a View/Context/Drawable - `@StringRes`/`@DrawableRes` ints are
 * the way to carry resources.
 */
abstract class BaseViewModel<S : Any>(initialState: S) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    protected val state: S get() = _uiState.value

    protected fun setState(reducer: S.() -> S) = _uiState.update(reducer)

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    protected fun effect(effect: UiEffect) {
        _effects.trySend(effect)
    }

    /**
     * The api throws for everything the offline mode cannot do, and the messages are what the ui
     * shows, so every screen ends up doing the same thing with a failed call.
     */
    protected fun onError(err: Throwable) {
        err.printStackTrace()
        effect(ShowSnackbar(text = err.message))
    }
}
