package com.scurab.android.zumpareader.ui.compose

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Everything a screen can navigate to, so a `Screen(uiState, eventHandler)` stays at two arguments
 * and the host holds no screen-specific code.
 *
 * Backed by the FragmentManager today; when nav-compose lands it gets a second implementation and
 * nothing inside a screen changes.
 */
interface Navigator {
    fun openThread(threadId: String)

    /**
     * No shared element transition, unlike the View implementation - which is what fixes the image
     * vanishing from the list (UPGRADE_PLAN.md E1). If it is wanted back it returns as
     * SharedTransitionLayout, which has no recycled-view problem.
     */
    fun openImage(url: String)

    fun openLink(url: String)

    fun openSettings()

    fun openPostDialog(threadId: String? = null, flag: Int? = null)

    fun openOfflineDownload()

    fun back()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided, host the screen with zumpaContent {}")
}
