package com.scurab.android.zumpareader.ui.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Fixed dimensions from `values/dimens.xml` and the size attrs of `theme_black.xml`. */
@Immutable
data class AppSizes(
    /** `?itemStateWidth` - the coloured bar down the left of a thread row. */
    val threadStateBarWidth: Dp,
    /** `subjectTextMinHeight`, which the xml points at `text_mid` (16sp used as a dimension). */
    val subjectMinHeight: Dp,
    val divider: Dp,
    val scrollBar: Dp,
    val responseEditTextMinHeight: Dp,
    val newMessageEditTextMinHeight: Dp,
    val dialogOfflineMinWidth: Dp,
    val progressBar: Dp,
    val buttonMinSize: Dp,
    val urlButtonStrokeWidth: Dp,
    val headerButtonTopGap: Dp,
    /** `?actionBarSize` - what the AppCompat Toolbar was, against M3's taller 64dp default. */
    val topBarHeight: Dp,
    /** An `ImageButton` of `item_main_list_menu`: a 24dp icon inside `?buttonBackground`. */
    val rowMenuButton: Dp,
    val rowMenuIcon: Dp,
    /** What an inline image shows instead of itself when the picture is gone. */
    val brokenImageIcon: Dp,
)

val DefaultSizes = AppSizes(
    threadStateBarWidth = 2.dp,
    subjectMinHeight = 16.dp,
    divider = 1.dp,
    scrollBar = 3.dp,
    responseEditTextMinHeight = 48.dp,
    newMessageEditTextMinHeight = 144.dp,
    dialogOfflineMinWidth = 250.dp,
    progressBar = 24.dp,
    buttonMinSize = 48.dp,
    urlButtonStrokeWidth = 1.dp,
    headerButtonTopGap = 52.dp,
    topBarHeight = 56.dp,
    rowMenuButton = 36.dp,
    rowMenuIcon = 24.dp,
    brokenImageIcon = 48.dp,
)

val LocalAppSizes = staticCompositionLocalOf { DefaultSizes }
