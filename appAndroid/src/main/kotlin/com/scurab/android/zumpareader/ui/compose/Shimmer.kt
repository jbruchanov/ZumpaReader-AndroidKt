package com.scurab.android.zumpareader.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme

/**
 * A highlight sweeping across whatever it is applied to, for a box holding space for something that
 * has not arrived yet.
 *
 * Drawn *behind* the content and against the row colours - secondaryBackground with a warm highlight
 * - so it reads as the app rather than as a grey skeleton borrowed from a lighter design.
 *
 * The highlight is flattened onto the base with [compositeOver] rather than left translucent: as a
 * gradient stop the 25% orange composites against whatever is under the box, which on this theme is
 * near black, and the sweep came out a dozen values brighter than the base - invisible. Flattened it
 * is a warm band; at 50% it stopped being a band and became a colour field.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = AppTheme.colorScheme.secondaryBackground
    val highlight = AppTheme.colorScheme.context25p.compositeOver(base)
    val transition = rememberInfiniteTransition(label = "shimmer")
    //0..1 is one pass of the highlight across the box, then it starts over from the left
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SWEEP_DURATION_MS, easing = LinearEasing),
        ),
        label = "sweep",
    )

    return drawBehind {
        //the band is a third of the width and travels from just off the left to just off the right,
        //so the box is never fully lit and never fully flat
        val band = size.width * BAND_FRACTION
        val head = -band + (size.width + band * 2) * progress
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(head, 0f),
                end = Offset(head + band, size.height),
            ),
        )
    }
}

private const val SWEEP_DURATION_MS = 1_200
private const val BAND_FRACTION = 0.33f
