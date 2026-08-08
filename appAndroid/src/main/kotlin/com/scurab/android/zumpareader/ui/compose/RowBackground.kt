package com.scurab.android.zumpareader.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme

/**
 * The alternating list row background, a direct translation of
 * `drawable/item_list_background_theme_black.xml`.
 *
 * That drawable is a level-list the adapters drove with `position % 2`, and each level is a selector
 * with its own default, pressed and selected appearance. Selection is a layer-list - the highlight
 * sits *over* the row's base colour rather than replacing it - which is why this paints twice.
 *
 * [interactionSource] is the one the row's `clickable`/`combinedClickable` was given; without it the
 * row simply never renders a pressed state.
 */
@Composable
fun Modifier.zumpaRowBackground(
    index: Int,
    isSelected: Boolean = false,
    interactionSource: InteractionSource? = null,
): Modifier {
    val isPressed = interactionSource?.collectIsPressedAsState()?.value ?: false
    val isEven = index % 2 == 0
    val base = when {
        isPressed && isEven -> AppTheme.colorScheme.rowEvenPressed
        isPressed -> AppTheme.colorScheme.rowOddPressed
        isEven -> AppTheme.colorScheme.rowEven
        else -> AppTheme.colorScheme.rowOdd
    }
    return background(base).let {
        if (isSelected) it.background(AppTheme.colorScheme.selectedBackground) else it
    }
}
