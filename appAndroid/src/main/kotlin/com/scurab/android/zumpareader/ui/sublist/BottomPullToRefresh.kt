package com.scurab.android.zumpareader.ui.sublist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Pull **up** past the last item to reload - the thread screen's original gesture, and the only
 * reason `swipy` was ever a dependency. `androidx.swiperefreshlayout` cannot do a bottom direction
 * and neither can M3's PullToRefreshBox, so this is hand written.
 *
 * It only claims overscroll: the list scrolls normally until it is at the end and the drag is still
 * going up, at which point the leftover is accumulated. Past [threshold] of accumulated overscroll
 * it fires once and disarms until the finger lifts, so a long drag cannot trigger twice.
 *
 * Deliberately does not consume the scroll, so the list keeps its own overscroll effect - the
 * gesture is observed rather than intercepted, which is what keeps the fling behaviour intact.
 *
 * The accumulated distance is published into [state] as a 0..1 fraction, which is what
 * [BottomPullToRefreshIndicator] draws, so the spinner rises out of the bottom edge with the finger
 * instead of dropping in from the top. Pair the two with the same [state] and [isRefreshing].
 */
fun Modifier.bottomPullToRefresh(
    listState: LazyListState,
    state: PullToRefreshState,
    isRefreshing: Boolean,
    enabled: Boolean,
    threshold: Dp = BottomPullToRefreshDefaults.Threshold,
    onTriggered: () -> Unit,
): Modifier = composed {
    val currentOnTriggered by rememberUpdatedState(onTriggered)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentIsRefreshing by rememberUpdatedState(isRefreshing)
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val scope = rememberCoroutineScope()

    val connection = remember(listState, state, scope, thresholdPx) {
        object : NestedScrollConnection {
            private var accumulated = 0f

            //false once this drag has fired, from where on isRefreshing owns the indicator
            private var armed = true

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!currentEnabled || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                //available.y < 0 is a drag upwards the list could not use, i.e. it is at the end
                if (available.y < 0f && armed && listState.isAtEnd()) {
                    accumulated = (accumulated - available.y).coerceAtMost(thresholdPx)
                    scope.launch { state.snapTo(accumulated / thresholdPx) }
                    if (accumulated >= thresholdPx) {
                        armed = false
                        currentOnTriggered()
                    }
                } else if (available.y > 0f && armed && accumulated > 0f) {
                    //dragged back down without committing, so put the spinner away again
                    accumulated = 0f
                    scope.launch { state.animateToHidden() }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                //a drag that started a reload leaves the spinner up until it lands - anything else
                //puts it away, including a drag that fired at a moment the reload declined to run
                if (accumulated > 0f && !currentIsRefreshing) {
                    state.animateToHidden()
                }
                accumulated = 0f
                armed = true
                return Velocity.Zero
            }
        }
    }
    nestedScroll(connection)
}

/**
 * The mirror image of `PullToRefreshDefaults.Indicator`: the same spinner in the same container,
 * hidden below the bottom edge at rest and travelling up as [state] fills. Align it to
 * [Alignment.BottomCenter] in the box that holds the list.
 *
 * M3's own indicator cannot be re-aligned to do this - its offset is `fraction * maxDistance -
 * height` against a clip that keeps the top half, which pushes it off the bottom of the screen
 * wherever it is placed.
 */
@Composable
fun BottomPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = PullToRefreshDefaults.indicatorContainerColor,
    color: Color = PullToRefreshDefaults.indicatorColor,
    maxDistance: Dp = PullToRefreshDefaults.IndicatorMaxDistance,
) {
    //the gesture drives state up to 1f and stops there, so the way back down is keyed on refreshing
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) state.animateToThreshold() else state.animateToHidden()
    }

    Box(
        modifier = modifier
            .size(SpinnerContainerSize)
            .drawWithContent {
                clipRect(
                    left = -Float.MAX_VALUE,
                    top = -Float.MAX_VALUE,
                    right = Float.MAX_VALUE,
                    bottom = size.height,
                ) {
                    this@drawWithContent.drawContent()
                }
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeWithLayer(
                        0,
                        0,
                        layerBlock = {
                            val showElevation = state.distanceFraction > 0f || isRefreshing
                            translationY =
                                size.height - state.distanceFraction * maxDistance.roundToPx()
                            shadowElevation =
                                if (showElevation) PullToRefreshDefaults.Elevation.toPx() else 0f
                            shape = PullToRefreshDefaults.indicatorShape
                            clip = true
                        },
                    )
                }
            }
            .background(color = containerColor, shape = PullToRefreshDefaults.indicatorShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                strokeWidth = StrokeWidth,
                color = color,
                modifier = Modifier.size(SpinnerSize),
            )
        } else {
            //determinate while the finger is down, so the ring fills as the threshold approaches
            CircularProgressIndicator(
                progress = { state.distanceFraction.coerceIn(0f, 1f) },
                strokeWidth = StrokeWidth,
                color = color,
                trackColor = Color.Transparent,
                gapSize = 0.dp,
                modifier = Modifier.size(SpinnerSize),
            )
        }
    }
}

object BottomPullToRefreshDefaults {
    /**
     * How far the list has to be dragged past its end before the reload fires. In dp rather than
     * the raw 180px this used to be, which was a 45dp pull on a 4x phone and a 180dp one on a 1x
     * tablet.
     */
    val Threshold = 64.dp
}

private fun LazyListState.isAtEnd(): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return false
    //afterContentPadding, because the list reserves the navigation bar inset down there - without it
    //the end would read as reached while there was still a scroll left
    return last.index == info.totalItemsCount - 1 &&
        last.offset + last.size <=
        info.viewportEndOffset - info.afterContentPadding + END_TOLERANCE_PX
}

private const val END_TOLERANCE_PX = 2
private val StrokeWidth = 2.5.dp
private val SpinnerSize = 16.dp
private val SpinnerContainerSize = 40.dp
