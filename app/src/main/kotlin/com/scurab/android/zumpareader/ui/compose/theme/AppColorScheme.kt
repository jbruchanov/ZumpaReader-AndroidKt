package com.scurab.android.zumpareader.ui.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours of `values/theme_black.xml`, which is the app's only theme.
 *
 * Names follow the xml attrs so a widget being converted maps one to one:
 * `?contextColor` -> `AppTheme.colorScheme.context`, `?subjectColor` -> `…subject`, and so on.
 * Attrs that were only aliases in the xml (`nickNameColor` = `?contextColor`) are kept as separate
 * properties here, so a screen can diverge later without hunting down every use of `context`.
 */
@Immutable
data class AppColorScheme(
    //region raw palette - values/colors.xml
    val context: Color,
    val context50p: Color,
    val context25p: Color,
    val contextText: Color,
    /** Disabled state of `color/context_color_black.xml`. */
    val contextTextDisabled: Color,
    val contextText2: Color,
    val primaryText: Color,
    val primaryBackground: Color,
    val secondaryBackground: Color,
    val selectedBackground: Color,
    val hint: Color,
    //endregion

    //region semantic - the ?attr each widget actually referenced
    val nickName: Color,
    val subject: Color,
    val author: Color,
    val date: Color,
    val threads: Color,
    val buttonText: Color,
    val message: Color,
    val scrollbar: Color,
    //endregion

    //region content
    val ratingGood: Color,
    val ratingBad: Color,
    val threadStateNew: Color,
    val threadStateUpdated: Color,
    val threadStateOwn: Color,
    val threadStateResponseForYou: Color,
    //endregion
)

private object Palette {
    val YellowOrange = Color(0xFFFFA710)
    val YellowOrange50p = Color(0x80FFA710)
    val YellowOrange25p = Color(0x40FFA710)
    val BlueGray = Color(0xFF0D8AAC)
    val Black = Color(0xFF000000)
    val Dark = Color(0xFF202020)
    val Grey = Color(0xFF808080)
    val White = Color(0xFFFFFFFF)
    val RatingGood = Color(0xFF00AA00)
    val RatingBad = Color(0xFFDD0000)
    val ItemStateNew = Color(0x7000FF00)
    val ItemStateUpdated = Color(0xFFFFFF00)
    val ItemStateOwn = Color(0xFF00FFFF)
    val ItemStateHasMsg = Color(0xFFFF0000)
}

/** `ThemeBlack`, the app's only theme. */
val BlackColorScheme = AppColorScheme(
    context = Palette.YellowOrange,
    context50p = Palette.YellowOrange50p,
    context25p = Palette.YellowOrange25p,
    contextText = Palette.YellowOrange,
    contextTextDisabled = Palette.YellowOrange25p,
    contextText2 = Palette.BlueGray,
    primaryText = Palette.White,
    primaryBackground = Palette.Black,
    secondaryBackground = Palette.Dark,
    selectedBackground = Palette.YellowOrange25p,
    hint = Palette.Grey,

    nickName = Palette.YellowOrange,
    subject = Palette.White,
    author = Palette.YellowOrange,
    date = Palette.White,
    threads = Palette.White,
    buttonText = Palette.YellowOrange,
    message = Palette.Black,
    scrollbar = Palette.YellowOrange50p,

    ratingGood = Palette.RatingGood,
    ratingBad = Palette.RatingBad,
    threadStateNew = Palette.ItemStateNew,
    threadStateUpdated = Palette.ItemStateUpdated,
    threadStateOwn = Palette.ItemStateOwn,
    threadStateResponseForYou = Palette.ItemStateHasMsg,
)

val LocalAppColorScheme = staticCompositionLocalOf { BlackColorScheme }
