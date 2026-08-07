package com.scurab.android.zumpareader.ui.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import com.scurab.android.zumpareader.ui.mainlist.MainListScreen
import com.scurab.android.zumpareader.ui.sublist.SubListScreen

/**
 * The tablet's two panes. Replaces `fragment_tablet.xml` and its two FrameLayouts.
 *
 * The detail pane is started with an empty thread id on purpose: on a tablet the list pane writes
 * to [com.scurab.android.zumpareader.repository.SelectedThreadStore] and the detail ViewModel
 * collects it, so the panes never talk to each other directly.
 */
@Composable
fun TwoPaneScreen() {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(LIST_WEIGHT)) {
            MainListScreen()
        }
        Box(
            Modifier
                .width(AppTheme.sizes.divider)
                .fillMaxHeight()
                .background(AppTheme.colorScheme.context25p)
        )
        Box(Modifier.weight(DETAIL_WEIGHT)) {
            SubListScreen(threadId = "")
        }
    }
}

private const val LIST_WEIGHT = 0.4f
private const val DETAIL_WEIGHT = 0.6f
