package com.scurab.android.zumpareader.ui.image

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.test.Fixtures
import com.scurab.android.zumpareader.test.failed
import com.scurab.android.zumpareader.test.imageBitmap
import com.scurab.android.zumpareader.test.loaded
import com.scurab.android.zumpareader.test.mock
import com.scurab.android.zumpareader.ui.compose.LocalNavigator
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import org.koin.androidx.compose.koinViewModel

/**
 * [url] is the screen's argument. It comes from the intent today and from the back stack entry once
 * nav-compose lands; either way the host only hands it over, it does no wiring.
 */
@Composable
fun ImageScreen(url: String, vm: ImageViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is ImageEffect.OpenInBrowser -> {
                    navigator.openLink(effect.url)
                    navigator.back()
                }

                is ImageEffect.Close -> navigator.back()
            }
        }
    }
    LaunchedEffect(url) { vm.start(url) }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val eventHandler = vm
    ImageScreen(uiState, eventHandler)
}

@Composable
private fun ImageScreen(uiState: ImageUiState, eventHandler: ImageEventHandler) {
    BackHandler { eventHandler.onCloseRequested() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.primaryBackground),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState) {
            is ImageUiState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(AppTheme.sizes.progressBar),
                color = AppTheme.colorScheme.context,
            )

            is ImageUiState.Loaded -> ZoomableImage(uiState.bitmap.asImageBitmap())

            //momentary - the browser is already opening and the screen is closing behind it
            is ImageUiState.Failed -> Text(
                text = stringResource(R.string.unable_to_finish_operation),
                style = AppTheme.typography.subject,
                color = AppTheme.colorScheme.primaryText,
            )
        }
    }
}

/**
 * Replaces `pinchtozoom`'s `ImageMatrixTouchHandler`, a support-library View dependency and one of
 * the two things pinning Jetifier.
 *
 * Pan is clamped to the scaled bounds so the image cannot be flung off screen, and a double tap
 * toggles between fit and [DOUBLE_TAP_SCALE].
 */
@Composable
private fun ZoomableImage(bitmap: ImageBitmap, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun clamped(candidate: Offset, forScale: Float): Offset {
        val maxX = (viewport.width * (forScale - 1f)) / 2f
        val maxY = (viewport.height * (forScale - 1f)) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        val next = if (scale > 1f) MIN_SCALE else DOUBLE_TAP_SCALE
                        scale = next
                        offset = if (next == MIN_SCALE) Offset.Zero else clamped(offset, next)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    scale = next
                    offset = clamped(offset + pan, next)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 8f
private const val DOUBLE_TAP_SCALE = 2.5f

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ImageScreenLoadingPreview() = AppTheme {
    ImageScreen(ImageUiState.Loading, mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ImageScreenLoadedPreview() = AppTheme {
    ImageScreen(Fixtures.Image.loaded(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ImageScreenFailedPreview() = AppTheme {
    ImageScreen(Fixtures.Image.failed(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 200, heightDp = 200)
@Composable
private fun ZoomableImagePreview() = AppTheme {
    ZoomableImage(Fixtures.Image.imageBitmap())
}
