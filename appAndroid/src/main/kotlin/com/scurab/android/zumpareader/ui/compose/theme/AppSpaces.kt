package com.scurab.android.zumpareader.ui.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The `gap_*` dimens of `values/dimens.xml`, plus the two margins defined next to them. */
@Immutable
data class AppSpaces(
    val hairline: Dp,
    val tiny: Dp,
    val small: Dp,
    val normal: Dp,
    val normalWithGap: Dp,
    val mid: Dp,
    val large: Dp,
    val fabMargin: Dp,
    val activityHorizontalMargin: Dp,
    val activityVerticalMargin: Dp,
    /** `?listItemPadding`, which `theme_black.xml` points at `gap_normal`. */
    val listItemPadding: Dp,
)

val DefaultSpaces = AppSpaces(
    hairline = 1.dp,
    tiny = 2.dp,
    small = 4.dp,
    normal = 8.dp,
    normalWithGap = 10.dp,
    mid = 12.dp,
    large = 16.dp,
    fabMargin = 16.dp,
    activityHorizontalMargin = 16.dp,
    activityVerticalMargin = 16.dp,
    listItemPadding = 8.dp,
)

val LocalAppSpaces = staticCompositionLocalOf { DefaultSpaces }
