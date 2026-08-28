package com.scurab.zumpareader.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.scurab.android.zumpareader.util.looksLikeImageUrl
import java.awt.Desktop
import java.net.URI
import java.util.Locale

/**
 * The urls a post carries, rendered the way the phone renders them: a picture inline, anything else
 * as a button with the address on it.
 *
 * `ZumpaThreadItem.urls` is filled by the shared parser, so this is the same list the Android rows
 * are built from - the desktop was simply not looking at it, and posts that were mostly a link came
 * out as a line of unclickable text.
 *
 * The split is `looksLikeImageUrl()`, out of `:shared`, so both apps agree on what counts as a
 * picture.
 */
@Composable
internal fun PostUrls(urls: List<String>) {
    if (urls.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        urls.forEach { url ->
            if (url.looksLikeImageUrl()) InlineImage(url) else UrlButton(url)
        }
    }
}

/**
 * A picture, and the address as something to press when it will not load - which is what the phone
 * does with a broken one: the row collapses to the button rather than holding a space that stays
 * empty.
 */
@Composable
private fun InlineImage(url: String) {
    val painter = rememberAsyncImagePainter(model = url)
    val state by painter.state.collectAsState()

    when (state) {
        is AsyncImagePainter.State.Error -> UrlButton(url)

        is AsyncImagePainter.State.Success -> Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = IMAGE_MAX_HEIGHT)
                .clip(RoundedCornerShape(4.dp))
                .clickable { openInBrowser(url) },
        )

        //nothing has come back yet, so hold the space a picture will want
        else -> Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(IMAGE_PLACEHOLDER_RATIO)
                .clip(RoundedCornerShape(4.dp))
                .heightIn(max = IMAGE_MAX_HEIGHT),
        )
    }
}

/** The phone's url button: the orange outline, the address inside it, the middle elided. */
@Composable
private fun UrlButton(url: String) {
    val label = remember(url) { url.uppercase(Locale.ROOT) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, Accent, RoundedCornerShape(5.dp))
            .clickable { openInBrowser(url) }
            .heightIn(min = URL_BUTTON_MIN_HEIGHT)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Accent,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Out of the app entirely, which is the only place a link can go here - there is no in-app viewer
 * on the desktop, and the phone hands a plain link to the browser too.
 *
 * Guarded because a headless or unusual desktop has no browser to hand it to, and a link that
 * cannot be opened is not worth taking the window down for.
 */
private fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        ) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

private val IMAGE_MAX_HEIGHT = 420.dp
private val URL_BUTTON_MIN_HEIGHT = 40.dp

/** Nothing is known about a picture before it lands, and 16:9 is the least surprising guess. */
private const val IMAGE_PLACEHOLDER_RATIO = 16f / 9f
