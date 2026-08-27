package com.scurab.android.zumpareader.arch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the window is wide enough to show the thread list and a thread side by side.
 *
 * This was `DeviceConfig(isTablet)`, read once from `R.bool.is_tablet`, which made the two-pane
 * layout a property of the *device*. It is a property of the *window*: a phone in landscape is as
 * wide as the tablet that resource was written for, and unlike a device it changes while the app is
 * running - hence a flow rather than a value.
 *
 * Kept injectable for the same reason `DeviceConfig` was: the two-pane behaviour is a constructor
 * input a test can set, instead of a `resources.getBoolean` call inside the screen.
 */
class WindowLayout(isTwoPane: Boolean = false) {

    private val _isTwoPane = MutableStateFlow(isTwoPane)
    val isTwoPane: StateFlow<Boolean> = _isTwoPane.asStateFlow()

    /** Fed from the activity and from composition - the only places the window width is known. */
    fun onWidthChanged(widthDp: Int) {
        _isTwoPane.value = widthDp >= TWO_PANE_MIN_WIDTH_DP
    }

    companion object {
        /**
         * `sw600dp` was the bucket `is_tablet` used, so a real tablet keeps behaving exactly as it
         * did. A phone crosses it in landscape and not in portrait, which is the point of the move:
         * the pattern follows the window, not the hardware.
         */
        const val TWO_PANE_MIN_WIDTH_DP = 600

        /**
         * Below this a window is short - Material's compact height, which a phone in landscape is
         * and a tablet either way up is not. A phone in landscape is exactly the window that clears
         * [TWO_PANE_MIN_WIDTH_DP] while having barely a third of the height, so height has to be
         * asked about separately from width.
         *
         * Two things read it. A screen only goes in a dialog above it, because below it the dialog
         * would be a sliver with the keyboard over most of it. And the post screen lays itself out
         * differently below it: everything scrolls, including the action row, and the message field
         * is pinned to a couple of lines instead of stretching to fill a height that is not there.
         */
        const val COMPACT_HEIGHT_DP = 480
    }
}
