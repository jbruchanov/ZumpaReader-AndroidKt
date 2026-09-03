package com.scurab.android.zumpareader.ui.compose

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The compose replacement for [android.widget.Toast]: a single [SnackbarHostState] mounted at the
 * host level and reachable from every screen through [LocalSnackbarController], so a screen only
 * has to say `snackbar.show(...)` without threading a state around.
 *
 * Fire-and-forget: [show] returns straight away and the snackbar plays out on the host's scope, so
 * it survives the composition that requested it going away - the way a toast used to.
 *
 * Latest wins, no stacking: a new [show] cancels whatever is on screen and puts itself in its
 * place. `showSnackbar` suspends until the snackbar goes away, so cancelling the coroutine that is
 * showing it dismisses it - and the new [launch] follows on the same scope, which is enough for the
 * host to pick the new message up on the next frame.
 */
class SnackbarController(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
    private val resources: Resources,
) {
    private var currentJob: Job? = null

    fun show(text: String) {
        currentJob?.cancel()
        currentJob = scope.launch { hostState.showSnackbar(text) }
    }

    fun show(@StringRes resId: Int) {
        show(resources.getString(resId))
    }
}

val LocalSnackbarController = staticCompositionLocalOf<SnackbarController> {
    error("No SnackbarController provided, the screen has to be hosted by ZumpaNavHost")
}
