package com.scurab.zumpareader.desktop

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * The mobile palette, and a Material theme built out of it.
 *
 * The values are `:appAndroid`'s `Palette` - the same black, the same `#FFA710`, the same 25%
 * orange for a divider - because the two are meant to look like one product. Copied rather than
 * shared: the palette lives in `:appAndroid`'s theme package, which is an Android module, and
 * moving it into `:shared` belongs to merging the two UIs (phase 3 in `KMP_PLAN.md`).
 *
 * The theme is the part that was missing. Ad-hoc colours were passed to the app's own
 * composables, but every Material component underneath - the dialogs, the text fields, the
 * overflow menu - was left on M3's default scheme, which is a *light* one. So a black app opened
 * white dialogs with dark text in them.
 *
 * Container colours are all black rather than mobile's `#202020` surface. That follows the app: its
 * write surfaces were moved off the grey for the same reason, so a panel and a dialog would not
 * disagree about what a background is.
 */
@Composable
internal fun DesktopTheme(content: @Composable () -> Unit) {
    val colors = remember {
        darkColorScheme(
            primary = Accent,
            onPrimary = Content,
            secondary = Accent,
            onSecondary = Content,
            background = Background,
            onBackground = Content,
            surface = Background,
            onSurface = Content,
            surfaceVariant = Background,
            onSurfaceVariant = Content,
            //M3 works a dialog's container out of these, so they have to be black too or an
            //AlertDialog arrives with a tinted grey plate of its own
            surfaceContainer = Background,
            surfaceContainerLow = Background,
            surfaceContainerLowest = Background,
            surfaceContainerHigh = Background,
            surfaceContainerHighest = Background,
            outline = DividerColor,
            error = Error,
        )
    }
    MaterialTheme(
        colorScheme = colors,
        //the app sizes its own text, as the Android one does - see AppTypography there
        typography = Typography(),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(color = Content),
            content = content,
        )
    }
}

internal val Background = Color(0xFF000000)
internal val Accent = Color(0xFFFFA710)
internal val Content = Color(0xFFFFFFFF)
internal val Muted = Color(0xFF808080)
internal val RowEven = Color(0xFF000000)
internal val RowOdd = Color(0xFF1A1A1A)
internal val DividerColor = Color(0x40FFA710)
internal val SelectedRow = Color(0x30FFA710)
internal val Error = Color(0xFFDD0000)
