package com.scurab.android.zumpareader.ui.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** The corner radii the xml drawables use, so a converted widget keeps its outline. */
@Immutable
data class AppShapes(
    /** `edit_text_background_radius` - 2dp. */
    val editText: Shape,
    /** `url_button_radius_corners_theme_black` - 5dp. */
    val button: Shape,
    val dialog: Shape,
)

val DefaultShapes = AppShapes(
    editText = RoundedCornerShape(2.dp),
    button = RoundedCornerShape(5.dp),
    dialog = RoundedCornerShape(2.dp),
)

val LocalAppShapes = staticCompositionLocalOf { DefaultShapes }
