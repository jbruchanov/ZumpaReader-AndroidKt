package com.scurab.android.zumpareader.ui.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import com.scurab.android.zumpareader.ui.mainlist.MainListScreen
import com.scurab.android.zumpareader.ui.sublist.SubListScreen

/**
 * The two panes, drawn wherever the window is wide enough - a tablet either way up, a phone on its
 * side. Replaces `fragment_tablet.xml` and its two FrameLayouts.
 *
 * The detail pane is started with an empty thread id on purpose: the list pane writes to
 * [com.scurab.android.zumpareader.repository.SelectedThreadStore] and the detail ViewModel collects
 * it, so the panes never talk to each other directly.
 *
 * Each pane only touches one edge of the window, so each has the *other* edge's inset consumed for
 * it. `WindowInsets.safeDrawing` is a property of the window and knows nothing about where a
 * composable sits in it, so without this both panes would read the same left and right insets and
 * pad the two sides of the divider - in the middle of the screen - as if they were screen edges.
 * Start and end rather than left and right, because the manifest says `supportsRtl`.
 */
@Composable
fun TwoPaneScreen() {
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(LIST_WEIGHT)
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
        ) {
            MainListScreen()
        }
        Box(
            Modifier
                .width(AppTheme.sizes.divider)
                .fillMaxHeight()
                .background(AppTheme.colorScheme.context25p)
        )
        Box(
            Modifier
                .weight(DETAIL_WEIGHT)
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
        ) {
            SubListScreen(threadId = "")
        }
    }
}

private const val LIST_WEIGHT = 0.4f
private const val DETAIL_WEIGHT = 0.6f
