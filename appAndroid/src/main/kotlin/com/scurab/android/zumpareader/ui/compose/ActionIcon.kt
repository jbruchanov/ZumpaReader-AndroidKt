package com.scurab.android.zumpareader.ui.compose

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme

/**
 * One button of a post or reply action row: `?imageButtonBackground` around a context-tinted icon,
 * which is what every ImageButton in `widget_post_message.xml` was.
 *
 * Shared rather than written twice, because the two rows that use it were the same widget before the
 * compose migration - `PostMessageView`, inflated once as the post screen and once as the thread`s
 * reply panel - and they are still meant to read as one thing.
 */
@Composable
fun ActionIcon(icon: Painter, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = if (enabled) {
                AppTheme.colorScheme.context
            } else {
                AppTheme.colorScheme.contextTextDisabled
            },
        )
    }
}
