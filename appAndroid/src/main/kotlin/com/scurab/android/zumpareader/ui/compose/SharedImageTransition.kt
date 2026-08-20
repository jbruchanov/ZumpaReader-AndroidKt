package com.scurab.android.zumpareader.ui.compose

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * The [SharedTransitionScope] of the whole nav host, or null where there is no host.
 *
 * Nullable on purpose. Previews and tests compose a screen with no `NavDisplay` above it, and
 * [LocalNavAnimatedContentScope] has no sensible value there - so this being null is the signal not
 * to read that one either.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Ties an inline image in a thread to the same image in the full screen viewer, so tapping it grows
 * into place instead of cutting.
 *
 * This is what `ActivityOptions.makeSceneTransitionAnimation` and the tapped `ImageView` used to do
 * - the reason link routing lived in the fragment until the compose migration dropped the transition
 * in C1. [url] is the key, which is what makes the pairing work: it is the one thing the row and the
 * viewer both know, and it is unique per image.
 *
 * `sharedElement` rather than `sharedBounds` because it is literally the same picture at both ends:
 * it scales the content between the two rects, where sharedBounds re-measures and would read as a
 * squash while the row shape becomes the screen shape.
 *
 * A no-op outside a nav host, so a preview of either screen still renders.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedImage(url: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    return with(shared) {
        this@sharedImage.sharedElement(
            sharedContentState = rememberSharedContentState(key = "image:$url"),
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }
}
