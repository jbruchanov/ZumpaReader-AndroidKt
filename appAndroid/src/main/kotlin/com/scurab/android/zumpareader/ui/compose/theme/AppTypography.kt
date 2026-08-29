package com.scurab.android.zumpareader.ui.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The `*TextSize` attrs of `theme_black.xml`, resolved through `values/dimens.xml`.
 *
 * Colour is deliberately not part of a [TextStyle] here, the same way Material keeps its typography
 * colourless - a style is applied with `color = AppTheme.colorScheme.subject` at the use site.
 */
@Immutable
data class AppTypography(
    /** `?actionBarTitleTextSize` - 20sp, the size the AppCompat Toolbar drew its title at. */
    val title: TextStyle,
    /** `nickTextSize` - 12sp */
    val nickName: TextStyle,
    /** `subjectTextSize` - 16sp */
    val subject: TextStyle,
    /** `authorTextSize` - 12sp */
    val author: TextStyle,
    /** `dateTextSize` - 12sp */
    val date: TextStyle,
    /**
     * The number of answers on a thread row: `dateTextSize` and bold, so it is the size of the time
     * beside it and differs from it only in weight.
     *
     * Not `threadsTextSize`, despite the name of the view it belongs to. That attr and `threadsColor`
     * were declared in `theme_black.xml` and referenced by nothing at all - no layout, no code - and
     * `item_main_list_content.xml` gave `@+id/threads` `?attr/dateTextSize` with
     * `android:textStyle="bold"`. Taking the attr at its name is how this came to be drawn 2sp
     * larger than it ever was.
     */
    val answerCount: TextStyle,
    /**
     * 14sp, the size a `TextView` with no `textSize` of its own drew at - the platform's
     * `Widget.TextView` points at `?attr/textAppearanceSmall`, which is 14sp. What the offline
     * dialog, the settings rows and the survey question were.
     *
     * Regular, not bold. It was called `threads` and carried the bold of [answerCount] with it,
     * which is how a dialog of plain labels came to be set in bold throughout.
     */
    val body: TextStyle,
    /** `buttonTextSize` - 12sp */
    val button: TextStyle,
    /** `surveyButtonTextSize` - 10.5sp */
    val surveyButton: TextStyle,
    /** `text_message` - 13sp */
    val message: TextStyle,
    /** The image screen's meta table header - 16sp bold */
    val tableHeader: TextStyle,
    /** The image screen's meta table cell - 16sp */
    val tableText: TextStyle,
)

val DefaultTypography = AppTypography(
    title = TextStyle(fontSize = 20.sp),
    nickName = TextStyle(fontSize = 12.sp),
    subject = TextStyle(fontSize = 16.sp),
    author = TextStyle(fontSize = 12.sp),
    date = TextStyle(fontSize = 12.sp),
    answerCount = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    body = TextStyle(fontSize = 14.sp),
    button = TextStyle(fontSize = 12.sp),
    surveyButton = TextStyle(fontSize = 10.5.sp),
    message = TextStyle(fontSize = 13.sp),
    tableHeader = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    tableText = TextStyle(fontSize = 16.sp),
)

val LocalAppTypography = staticCompositionLocalOf { DefaultTypography }
