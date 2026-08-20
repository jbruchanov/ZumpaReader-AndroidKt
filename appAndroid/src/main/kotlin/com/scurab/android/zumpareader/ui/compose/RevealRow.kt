package com.scurab.android.zumpareader.ui.compose

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme

/**
 * A list row whose context menu lives *underneath* it.
 *
 * `item_main_list.xml` was a FrameLayout with the menu included first and the content on top, and
 * `ToggleAdapter` opened it by animating the content's `translationX` by the menu's width - so the
 * buttons were never drawn over the row, they were uncovered by it sliding out of the way. This is
 * that, with the same decelerating slide.
 *
 * [background] is painted across the full row height behind the menu, the way the menu's own
 * `?threadItemBackgroundNoPressedState` did, so the uncovered strip is the row's colour rather than
 * the window's.
 */
@Composable
fun RevealRow(
    isOpen: Boolean,
    background: Color,
    modifier: Modifier = Modifier,
    menu: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    var menuWidth by remember { mutableIntStateOf(0) }
    val offset by animateIntAsState(
        targetValue = if (isOpen) menuWidth else 0,
        animationSpec = tween(durationMillis = REVEAL_DURATION_MS, easing = DecelerateEasing),
        label = "reveal",
    )

    Box(modifier.clipToBounds()) {
        //matchParentSize so the strip takes the height the content sets without adding to it
        Box(Modifier.matchParentSize().background(background))
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .onSizeChanged { menuWidth = it.width }
                .padding(horizontal = AppTheme.spaces.normal),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spaces.normal),
            verticalAlignment = Alignment.CenterVertically,
            content = menu,
        )
        //last, so it draws over the menu and takes the touches while the row is closed
        Box(Modifier.offset { IntOffset(offset, 0) }) { content() }
    }
}

/**
 * One button of a [RevealRow] menu: `?buttonBackground`, which is the same orange outline the url
 * buttons wear, around a context-tinted icon.
 *
 * A [Painter] rather than a drawable id, so a caller can hand it either a `material-icons-core`
 * vector or one of the icons in `res/drawable` - see the note on that dependency in the catalog.
 */
@Composable
fun RevealRowMenuButton(icon: Painter, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(AppTheme.sizes.rowMenuButton)
            .clip(AppTheme.shapes.button)
            .border(
                width = AppTheme.sizes.urlButtonStrokeWidth,
                color = AppTheme.colorScheme.context,
                shape = AppTheme.shapes.button,
            )
            .clickable(indication = ripple(), interactionSource = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = AppTheme.colorScheme.context,
            modifier = Modifier.size(AppTheme.sizes.rowMenuIcon),
        )
    }
}

/** `DecelerateInterpolator`, which is what `ToggleAdapter` animated the slide with. */
private val DecelerateEasing = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }

/** `ValueAnimator`'s default, which is what `view.animate()` ran at. */
private const val REVEAL_DURATION_MS = 300
