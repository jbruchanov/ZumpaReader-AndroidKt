package com.scurab.android.zumpareader.ui.compose

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme

/**
 * The alternating list row background, a direct translation of
 * `drawable/item_list_background_theme_black.xml`.
 *
 * That drawable is a level-list the adapters drove with `position % 2`, and each level is a selector
 * with its own default, pressed and selected appearance. Selection is a layer-list - the highlight
 * sits *over* the row's base colour rather than replacing it - which is why this paints twice.
 *
 * The pressed state is *not* here: rows carry a `ripple()` indication in the context colour, which
 * is what the selector's flat `black_yellow_pressed` fill used to stand in for.
 */
@Composable
fun Modifier.zumpaRowBackground(index: Int, isSelected: Boolean = false): Modifier {
    val base = background(zumpaRowColor(index))
    return if (isSelected) base.background(AppTheme.colorScheme.selectedBackground) else base
}

/**
 * The row's base colour on its own, for whatever has to paint the same thing behind the row -
 * `item_list_background_no_pressed_state_theme_black`, which is what the context menu sat on.
 */
@Composable
@ReadOnlyComposable
fun zumpaRowColor(index: Int): Color =
    if (index % 2 == 0) AppTheme.colorScheme.rowEven else AppTheme.colorScheme.rowOdd
