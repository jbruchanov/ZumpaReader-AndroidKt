package com.scurab.android.zumpareader.ui.tablet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
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
 *
 * Where the panes split depends on what is being split. A tablet has one flat screen and no natural
 * seam, so the ratio is a judgement: [LIST_WEIGHT] to [DETAIL_WEIGHT], the list being the narrower
 * of the two. A foldable has the seam decided for it - putting the divider anywhere but on the
 * hinge leaves one pane straddling it - so the fold's own position is used instead of the ratio,
 * and the divider is drawn as wide as the hinge so nothing has to be read across it.
 */
@Composable
fun TwoPaneScreen() {
    val fold = rememberVerticalFold()
    Row(Modifier.fillMaxSize()) {
        ListPane(if (fold == null) Modifier.weight(LIST_WEIGHT) else Modifier.width(fold.start))
        Box(
            Modifier
                //no hinge, or a hinge with no thickness to it - a foldable lying flat has a seam
                //but no gap - is the plain line it always was
                .width(
                    fold?.width?.takeIf { it > AppTheme.sizes.divider }
                        ?: AppTheme.sizes.divider
                )
                .fillMaxHeight()
                .background(AppTheme.colorScheme.context25p)
        )
        //With a fold, everything left over - asking for the remainder rather than measuring the
        //far half again keeps the two from disagreeing by a pixel. Without one, its share of the
        //ratio: weights are proportional, so `weight(1f)` here would read as 0.4 against 1.0 and
        //quietly turn the tablet split into 29/71.
        DetailPane(
            if (fold == null) Modifier.weight(DETAIL_WEIGHT) else Modifier.weight(1f)
        )
    }
}

@Composable
private fun RowScope.ListPane(modifier: Modifier) {
    Box(modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End))) {
        MainListScreen()
    }
}

@Composable
private fun RowScope.DetailPane(modifier: Modifier) {
    Box(modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))) {
        SubListScreen(threadId = "")
    }
}

/** Where a fold crosses the window from top to bottom, and how much of it the hinge takes up. */
private data class VerticalFold(val start: Dp, val width: Dp)

/**
 * The fold, if this is a device with one across the window the short way.
 *
 * `VERTICAL` is the orientation of the hinge itself, so it is the case that separates a window into
 * a left half and a right half - which is the only fold a Row of two panes can do anything with. A
 * horizontal one, the device held like a laptop, is left to the ratio.
 *
 * `isSeparating` is deliberately not required. It is false for a foldable lying flat, whose screen
 * really is continuous - but the seam is still there under the glass, and a divider sitting on it
 * reads better than one a few hundred pixels off it. What flat does change is the hinge
 * width, which is zero, and the divider falls back to its usual hairline for that.
 */
@Composable
private fun rememberVerticalFold(): VerticalFold? {
    val activity = LocalContext.current.findActivity() ?: return null
    val density = LocalDensity.current
    val info by remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }.collectAsStateWithLifecycle(initialValue = null)

    val bounds = info?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull { it.orientation == FoldingFeature.Orientation.VERTICAL }
        ?.bounds
        ?: return null

    return remember(bounds, density) {
        with(density) { VerticalFold(start = bounds.left.toDp(), width = bounds.width().toDp()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val LIST_WEIGHT = 0.4f
private const val DETAIL_WEIGHT = 0.6f
