package com.scurab.android.zumpareader.repository

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The full size bitmap for the image viewer.
 *
 * The viewer needs a real [Bitmap] rather than a composable image because it feeds it to a zoom
 * gesture, so this asks Coil for one directly. `allowHardware(false)` because a hardware bitmap
 * cannot be drawn into the transform the viewer applies.
 */
class ImageCacheRepository(
    private val context: Context,
    private val imageLoader: ImageLoader,
) {

    /** Null means give up - the viewer hands the url to a browser and closes. */
    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }
}
