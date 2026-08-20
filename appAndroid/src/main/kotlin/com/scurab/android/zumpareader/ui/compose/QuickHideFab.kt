package com.scurab.android.zumpareader.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import kotlin.math.max

/**
 * `QuickHideBehavior`, the `CoordinatorLayout.Behavior` that took the fab off screen while the list
 * was moving and brought it back the moment the list came back.
 *
 * Its arithmetic, kept: the accumulated *consumed* scroll resets every time the drag changes
 * direction, and half an action bar of travel in either direction flips the fab. A fling flips it
 * straight away, no threshold - which is what made it feel quick.
 *
 * Pair one state with one scrolling container ([Modifier.quickHide]) and one fab ([QuickHideFab]).
 *
 * The old behavior also gated itself on `ZumpaPrefs.isLoggedInNotOffline`; here that gate is already
 * the `canInteract`/`canPost` condition the screens wrap their fab in, so there is nothing to check.
 */
@Composable
fun rememberQuickHideState(): QuickHideState {
    //`?actionBarSize / 2`, which is what the behavior read out of the theme
    val threshold = with(LocalDensity.current) { (AppTheme.sizes.topBarHeight / 2).toPx() }
    val state = remember { QuickHideState() }
    state.thresholdPx = threshold
    return state
}

@Stable
class QuickHideState internal constructor() {

    /** Whether the fab should be showing. Driven by the scroll, read by [QuickHideFab]. */
    var isVisible by mutableStateOf(true)
        private set

    internal var thresholdPx: Float = 0f

    //the two `DIRECTION_*` ints of the original, in compose terms
    private var direction = NONE
    private var trigger = NONE
    private var distance = 0f

    /**
     * Deltas are flipped into the view convention the behavior was written in - there, a positive
     * `dy` meant scrolling towards the end of the content, where compose reports that as negative.
     */
    internal fun onDirection(delta: Float) {
        val forward = -delta
        if (forward > 0f && direction != FORWARD) {
            direction = FORWARD
            distance = 0f
        } else if (forward < 0f && direction != BACK) {
            direction = BACK
            distance = 0f
        }
    }

    internal fun onConsumed(delta: Float) {
        //consumed, not available: the distance the list actually travelled
        distance += -delta
        if (distance > thresholdPx && trigger != FORWARD) {
            trigger = FORWARD
            isVisible = false
        } else if (distance < -thresholdPx && trigger != BACK) {
            trigger = BACK
            isVisible = true
        }
    }

    internal fun onFling(velocity: Float) {
        val forward = -velocity
        if (forward > 0f && trigger != FORWARD) {
            trigger = FORWARD
            isVisible = false
        } else if (forward < 0f && trigger != BACK) {
            trigger = BACK
            isVisible = true
        }
    }

    private companion object {
        const val NONE = 0
        const val FORWARD = 1
        const val BACK = -1
    }
}

/** Put this on the scrolling container whose movement should hide the fab. */
fun Modifier.quickHide(state: QuickHideState): Modifier = nestedScroll(
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            state.onDirection(available.y)
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            state.onConsumed(consumed.y)
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            state.onFling(available.y)
            return Velocity.Zero
        }
    }
)

/**
 * Wraps a fab in the circular reveal the behavior ran through `ViewAnimationUtils`, growing from the
 * centre to `max(width, height)`.
 *
 * Once closed the fab leaves the composition entirely, which is the `visibility = INVISIBLE` the
 * animation listener used to set - a fab clipped down to nothing would still take taps.
 */
@Composable
fun QuickHideFab(state: QuickHideState, content: @Composable () -> Unit) {
    val revealed by animateFloatAsState(
        targetValue = if (state.isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = REVEAL_DURATION_MS),
        label = "quickHide",
    )
    if (revealed > 0f) {
        //no clip at rest, so the fab keeps its shadow whenever it is not mid-reveal
        Box(if (revealed >= 1f) Modifier else Modifier.clip(CircleReveal(revealed))) {
            content()
        }
    }
}

/** `createCircularReveal(target, width / 2, height / 2, 0f, max(width, height))`, as a [Shape]. */
private data class CircleReveal(private val fraction: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = max(size.width, size.height) * fraction
        val center = size.center
        val path = Path().apply {
            addOval(Rect(center - Offset(radius, radius), center + Offset(radius, radius)))
        }
        return Outline.Generic(path)
    }
}

/** `ValueAnimator`'s default, which is what the reveal animator ran at. */
private const val REVEAL_DURATION_MS = 300
