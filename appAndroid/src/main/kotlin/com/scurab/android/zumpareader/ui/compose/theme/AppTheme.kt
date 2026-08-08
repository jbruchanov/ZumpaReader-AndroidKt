package com.scurab.android.zumpareader.ui.compose.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * The app's design system, shaped like [MaterialTheme] so it reads the same way:
 * `AppTheme.colorScheme.context`, `AppTheme.spaces.normal`, …
 *
 * The values are the ones in `values/theme_black.xml`, `colors.xml` and `dimens.xml` - see the
 * individual classes, each property names the attr it came from.
 *
 * Material 3 sits underneath so its components (Scaffold, TextField, PullToRefreshBox) work, but it
 * is not the source of truth for anything the app draws itself. Its colour scheme is filled in from
 * the palette so that a stray Material default lands somewhere plausible rather than purple.
 */
@Composable
fun AppTheme(
    colorScheme: AppColorScheme = BlackColorScheme,
    typography: AppTypography = DefaultTypography,
    shapes: AppShapes = DefaultShapes,
    sizes: AppSizes = DefaultSizes,
    spaces: AppSpaces = DefaultSpaces,
    content: @Composable () -> Unit,
) {
    val material = remember(colorScheme) {
        darkColorScheme(
            primary = colorScheme.context,
            onPrimary = colorScheme.primaryBackground,
            secondary = colorScheme.contextText2,
            background = colorScheme.primaryBackground,
            onBackground = colorScheme.primaryText,
            surface = colorScheme.secondaryBackground,
            onSurface = colorScheme.primaryText,
            surfaceVariant = colorScheme.secondaryBackground,
            onSurfaceVariant = colorScheme.primaryText,
            error = colorScheme.ratingBad,
        )
    }

    CompositionLocalProvider(
        LocalAppColorScheme provides colorScheme,
        LocalAppTypography provides typography,
        LocalAppShapes provides shapes,
        LocalAppSizes provides sizes,
        LocalAppSpaces provides spaces,
    ) {
        MaterialTheme(
            colorScheme = material,
            //the app never used a type scale, only per-widget sizes - see AppTypography
            typography = Typography(),
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(color = colorScheme.primaryText),
                content = content,
            )
        }
    }
}

object AppTheme {
    val colorScheme: AppColorScheme
        @Composable @ReadOnlyComposable get() = LocalAppColorScheme.current

    val typography: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current

    val shapes: AppShapes
        @Composable @ReadOnlyComposable get() = LocalAppShapes.current

    val sizes: AppSizes
        @Composable @ReadOnlyComposable get() = LocalAppSizes.current

    val spaces: AppSpaces
        @Composable @ReadOnlyComposable get() = LocalAppSpaces.current
}
