package com.scurab.zumpareader.desktop

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/** `Palette.Dark`. Was `0xFF1A1A1A` here, which is a slightly different grey for no reason. */
internal val RowOdd = Color(0xFF202020)
internal val DividerColor = Color(0x40FFA710)

/** `selectedBackground`, which is the 25% orange - it was 0x30 here, again for no reason. */
internal val SelectedRow = Color(0x40FFA710)
internal val Error = Color(0xFFDD0000)

/**
 * The bar down the left of a thread row - `AppColorScheme.threadState*`, which the `LevelListDrawable`
 * before it drew. Green is deliberately translucent, as it always was.
 */
internal val StateNew = Color(0x7000FF00)
internal val StateUpdated = Color(0xFFFFFF00)
internal val StateOwn = Color(0xFF00FFFF)
internal val StateResponseForYou = Color(0xFFFF0000)

/**
 * `:appAndroid`'s `AppSpaces` and the `AppSizes` this module has any use for.
 *
 * Plain constants rather than a CompositionLocal-backed theme: there is one set of them and nothing
 * here overrides them, so the indirection would buy nothing. Named after the Android properties so
 * the two files can be read against each other.
 *
 * Rows were being padded 16dp horizontally against the phone's 8dp, which is what made the desktop
 * list look loose next to it.
 */
internal object Spaces {
    val tiny = 2.dp
    val small = 4.dp
    val normal = 8.dp
    val large = 16.dp
    val fabMargin = 16.dp

    /** `?listItemPadding`. The one number to change if the desktop list should sit tighter. */
    val listItemPadding = 8.dp
}

internal object Sizes {
    /** `?itemStateWidth`. */
    val threadStateBar = 2.dp
    val divider = 1.dp
    val progressBar = 24.dp

    /** `subjectTextMinHeight` - 16sp used as a dimension, so a one-line subject has a fixed height. */
    val subjectMinHeight = 16.dp
}

/** `AppTypography`, sized the same. The desktop had its own slightly smaller numbers. */
internal object FontSizes {
    val title = 20.sp
    val subject = 16.sp
    val author = 12.sp
    val date = 12.sp
    val nickName = 12.sp
    val message = 13.sp
    val button = 12.sp
}

/** `threadsTextSize` is 14sp **bold** - the answer count always was. */
internal val ThreadCountSize = 14.sp
