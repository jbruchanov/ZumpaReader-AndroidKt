package com.scurab.android.zumpareader.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.scurab.android.zumpareader.text.AnnotatedTextRenderer
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme

/**
 * The renderer, built from the current [AppTheme] colours and remembered so its caches survive
 * recomposition. Rows call it inside `remember(markup)`, so only what is on screen is rendered and
 * a repeat costs nothing.
 */
@Composable
fun rememberAnnotatedTextRenderer(): AnnotatedTextRenderer {
    val response = AppTheme.colorScheme.contextText2
    val good = AppTheme.colorScheme.ratingGood
    val bad = AppTheme.colorScheme.ratingBad
    return remember(response, good, bad) { AnnotatedTextRenderer(response, good, bad) }
}
