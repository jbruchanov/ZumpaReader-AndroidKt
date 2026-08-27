package com.scurab.android.zumpareader.ui.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Collapses a top app bar by however much the content actually scrolled, taking none of the scroll
 * for itself.
 *
 * M3's own connections claim the gesture for the bar first: `EnterAlwaysScrollBehavior.onPreScroll`
 * adds the whole delta to the height offset and then returns it as consumed, so a drag collapses
 * the header and only afterwards begins to move the list. One drag, two scrolls, and the second one
 * does not start until the first has finished.
 *
 * The behaviour's `onPostScroll` already does the right thing - `heightOffset += consumed.y` - it
 * simply never gets a look in while `onPreScroll` is eating the delta. So this is that half on its
 * own: watch what the list took, move the bar by the same amount, consume nothing. The two then
 * travel together, and a list with nothing to scroll leaves the bar alone because it consumes
 * nothing for the bar to follow.
 *
 * [TopAppBarState.heightOffset] coerces itself between its limit and zero, so there is no clamping
 * to do here. `contentOffset` is kept up to date because M3 works its scrolled appearance out from
 * it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSyncedTopAppBarScroll(state: TopAppBarState): NestedScrollConnection =
    remember(state) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                state.contentOffset += consumed.y
                state.heightOffset += consumed.y
                return Offset.Zero
            }
        }
    }
