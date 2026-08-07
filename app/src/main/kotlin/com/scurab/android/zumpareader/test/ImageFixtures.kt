package com.scurab.android.zumpareader.test

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.scurab.android.zumpareader.ui.image.ImageUiState

fun Fixtures.Image.loaded(): ImageUiState.Loaded = ImageUiState.Loaded(bitmap())

fun Fixtures.Image.failed(url: String = "https://zunpa.cz/broken.jpg"): ImageUiState.Failed =
    ImageUiState.Failed(url)

fun Fixtures.Image.imageBitmap(): ImageBitmap = bitmap().asImageBitmap()

/**
 * A checkerboard, so a preview of the zoom gesture shows something with edges to pan against.
 */
private fun bitmap(size: Int = 240, cells: Int = 6): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val cell = size / cells
    for (y in 0 until cells) {
        for (x in 0 until cells) {
            paint.color = if ((x + y) % 2 == 0) CHECKER_LIGHT else CHECKER_DARK
            canvas.drawRect(
                (x * cell).toFloat(),
                (y * cell).toFloat(),
                ((x + 1) * cell).toFloat(),
                ((y + 1) * cell).toFloat(),
                paint,
            )
        }
    }
    return bitmap
}

private const val CHECKER_LIGHT = 0xFFFFA710.toInt()
private const val CHECKER_DARK = 0xFF202020.toInt()
