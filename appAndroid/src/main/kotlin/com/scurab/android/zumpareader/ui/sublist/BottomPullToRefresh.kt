package com.scurab.android.zumpareader.ui.sublist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Pull **up** past the last item to reload - the thread screen's original gesture, and the only
 * reason `swipy` was ever a dependency. `androidx.swiperefreshlayout` cannot do a bottom direction
 * and neither can M3's PullToRefreshBox, so this is hand written.
 *
 * It only claims overscroll: the list scrolls normally until it is at the end and the drag is still
 * going up, at which point the leftover is accumulated. Past [THRESHOLD_PX] of accumulated
 * overscroll it fires once and disarms until the finger lifts, so a long drag cannot trigger twice.
 *
 * Deliberately does not consume the scroll, so the list keeps its own overscroll effect - the
 * gesture is observed rather than intercepted, which is what keeps the fling behaviour intact.
 */
fun Modifier.bottomPullToRefresh(
    listState: LazyListState,
    enabled: Boolean,
    onTriggered: () -> Unit,
): Modifier = composed {
    val currentOnTriggered by rememberUpdatedState(onTriggered)
    val currentEnabled by rememberUpdatedState(enabled)

    val connection = remember(listState) {
        object : NestedScrollConnection {
            private var accumulated = 0f
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
                if (available.y < 0f && listState.isAtEnd()) {
                    accumulated += -available.y
                    if (armed && accumulated >= THRESHOLD_PX) {
                        armed = false
                        currentOnTriggered()
                    }
                } else if (available.y > 0f) {
                    accumulated = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                accumulated = 0f
                armed = true
                return Velocity.Zero
            }
        }
    }
    nestedScroll(connection)
}

private fun LazyListState.isAtEnd(): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return false
    return last.index == info.totalItemsCount - 1 &&
        last.offset + last.size <= info.viewportEndOffset + END_TOLERANCE_PX
}

private const val THRESHOLD_PX = 180f
private const val END_TOLERANCE_PX = 2
