package com.scurab.android.zumpareader.ui.compose

import androidx.compose.runtime.staticCompositionLocalOf
import com.scurab.android.zumpareader.ui.post.PostPicker

/**
 * Everything a screen can navigate to, so a `Screen(uiState, eventHandler)` stays at two arguments
 * and the host holds no screen-specific code.
 *
 * Implemented by [com.scurab.android.zumpareader.ui.nav.BackStackNavigator] over the navigation-3
 * back stack. A screen never names a destination class, only what it wants to open.
 */
interface Navigator {
    fun openThread(threadId: String)

    /**
     * No shared element transition, unlike the View implementation - which is what fixes the image
     * vanishing from the list (UPGRADE_PLAN.md E1). If it is wanted back it returns as
     * NavDisplay's `sharedTransitionScope`, which has no recycled-view problem.
     */
    fun openImage(url: String)

    fun openLink(url: String)

    fun openSettings()

    fun openPostDialog(threadId: String? = null, picker: PostPicker? = null)

    fun openOfflineDownload()

    fun back()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided, the screen has to be hosted by ZumpaNavHost")
}
