package com.scurab.android.zumpareader.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Apps targeting SDK 35+ are always drawn edge to edge, the opt-out flag is ignored on Android 16.
 * The app has no design for it, so the insets are simply turned into padding to keep the pre-35 look.
 *
 * @param topInsetView optional view taking the top inset, useful for a toolbar which should keep
 * painting its own background behind the status bar. When null the receiver takes the top inset too.
 */
fun View.applySystemBarsAsPadding(topInsetView: View? = null) {
    val originalPadding = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    val topViewOriginalPaddingTop = topInsetView?.paddingTop ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
        topInsetView?.updatePadding(top = topViewOriginalPaddingTop + bars.top)
        view.updatePadding(
            left = originalPadding[0] + bars.left,
            top = originalPadding[1] + if (topInsetView == null) bars.top else 0,
            right = originalPadding[2] + bars.right,
            bottom = originalPadding[3] + maxOf(bars.bottom, ime.bottom)
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
