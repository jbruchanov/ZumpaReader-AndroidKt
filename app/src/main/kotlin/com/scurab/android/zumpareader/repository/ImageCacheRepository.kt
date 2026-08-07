package com.scurab.android.zumpareader.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.BaseDataSubscriber
import com.facebook.datasource.DataSource
import com.facebook.datasource.DataSources
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.image.CloseableStaticBitmap
import com.scurab.android.zumpareader.util.scaledImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Pulls a full size bitmap out of fresco for the image viewer.
 *
 * The viewer cannot use a fresco drawee - ImageMatrixTouchHandler does not work with one - so it
 * needs a plain Bitmap, which is why this reaches into the pipeline by hand.
 */
class ImageCacheRepository(private val context: Context) {

    /** The memory cache first, then the encoded image off disk or network. Null means give up. */
    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        fromBitmapCache(url) ?: fromEncoded(url)
    }

    private suspend fun fromBitmapCache(url: String): Bitmap? = suspendCancellableCoroutine { continuation ->
        val dataSource = Fresco.getImagePipeline()
            .fetchImageFromBitmapCache(scaledImageRequest(url, context), context)

        continuation.invokeOnCancellation { dataSource.close() }

        dataSource.subscribe(
            object : BaseDataSubscriber<CloseableReference<CloseableImage>>() {
                override fun onNewResultImpl(source: DataSource<CloseableReference<CloseableImage>>) {
                    if (!source.isFinished) return
                    val bitmap = (source.result?.get() as? CloseableStaticBitmap)?.underlyingBitmap
                    source.close()
                    if (continuation.isActive) continuation.resume(bitmap)
                }

                override fun onFailureImpl(source: DataSource<CloseableReference<CloseableImage>>) {
                    source.close()
                    if (continuation.isActive) continuation.resume(null)
                }
            },
            CallerThreadExecutor.getInstance()
        )
    }

    /**
     * [DataSources.waitForFinalResult] blocks. It used to be called straight from onCreate on the
     * main thread; here it is inside [Dispatchers.IO].
     */
    private fun fromEncoded(url: String): Bitmap? {
        val dataSource = Fresco.getImagePipeline()
            .fetchEncodedImage(scaledImageRequest(url, context), context)
        try {
            val reference = DataSources.waitForFinalResult(dataSource) ?: return null
            try {
                val buffer = reference.get()
                val bytes = ByteArray(buffer.size())
                buffer.read(0, bytes, 0, bytes.size)
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } finally {
                CloseableReference.closeSafely(reference)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        } finally {
            dataSource.close()
        }
    }
}
