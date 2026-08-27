package com.scurab.android.zumpareader.ui.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import java.util.Locale

/**
 * A url as something to press: `?buttonBackground`, the orange outline the link rows have always
 * worn, around the address itself.
 *
 * Shared because three places need the identical control - a link on its own line, the caption
 * above an inline picture, and what is left of that picture when it cannot be fetched.
 *
 * [MiddleEllipsis] rather than a trailing one is the point of showing the url at all: the host and
 * the file name are the two halves that say what the thing is, and a long path in between is the
 * part nobody reads.
 *
 * [onLongClick] is the old `onItemClick(url, longClick = true)` - hold an address to copy it.
 */
@Composable
fun UrlButton(
    url: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val label = remember(url) { url.uppercase(Locale.ROOT) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.button)
            .border(
                width = AppTheme.sizes.urlButtonStrokeWidth,
                color = AppTheme.colorScheme.context,
                shape = AppTheme.shapes.button,
            )
            .combinedClickable(
                indication = ripple(),
                interactionSource = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            //inside the border and the clickable, so the outline grows with the touch target
            //instead of leaving an invisible margin hanging off a small button
            .heightIn(min = AppTheme.sizes.buttonMinSize)
            .padding(AppTheme.spaces.listItemPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AppTheme.typography.button,
            color = AppTheme.colorScheme.buttonText,
            textAlign = TextAlign.Center,
            maxLines = URL_MAX_LINES,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val URL_MAX_LINES = 2
