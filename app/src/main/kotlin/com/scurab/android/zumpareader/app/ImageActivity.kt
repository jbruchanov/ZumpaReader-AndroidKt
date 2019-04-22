package com.scurab.android.zumpareader.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView
import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.common.memory.PooledByteBuffer
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.BaseDataSubscriber
import com.facebook.datasource.DataSource
import com.facebook.datasource.DataSources
import com.facebook.datasource.DataSubscriber
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.image.CloseableStaticBitmap
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.util.scaledImageRequest
import com.scurab.android.zumpareader.util.startLinkActivity
import org.jetbrains.anko.imageBitmap

/**
 * Created by Scurab on 08/09/2017.
 */
private val kUrl = "Url"

class ImageActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context, url: String): Intent {
            return Intent(context, ImageActivity::class.java).apply {
                putExtra(kUrl, url)
            }
        }
    }

    private val url: String by lazy { intent.getStringExtra(kUrl) }
    private lateinit var imageView : ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_image)
        imageView = findViewById(R.id.image)
        imageView.setOnTouchListener(ImageMatrixTouchHandler(this))
        loadImageFromCache(url)
    }

    private fun loadImageFromCache(url: String) {
        val imagePipeline = Fresco.getImagePipeline()
        //TODO: is there better way how to get a bitmap with not crashing in case of invalid image ?
        //ImageMatrixTouchHandler doesn't work with fresco imageview
        var dataSource = imagePipeline.fetchImageFromBitmapCache(scaledImageRequest(url, this), this)
        dataSource.subscribe(object : BaseDataSubscriber<CloseableReference<CloseableImage>>() {
            override fun onNewResultImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) {
                if (dataSource.isFinished) {
                    val bitmap = (dataSource.result?.get() as? CloseableStaticBitmap)?.underlyingBitmap
                    if (bitmap != null) {
                        imageView.imageBitmap = bitmap
                    } else {
                        loadImage(url)
                    }
                }
            }

            override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>?) {
                loadImage(url)
            }

        }, CallerThreadExecutor.getInstance())
    }

    private fun loadImage(url: String) {
        val imagePipeline = Fresco.getImagePipeline()
        var dataSource = imagePipeline.fetchEncodedImage(scaledImageRequest(this.url, this), this)
        try {
            val result = DataSources.waitForFinalResult(dataSource)
            if (result != null) {
                dataSource.subscribe(object : DataSubscriber<CloseableReference<PooledByteBuffer>> {
                    override fun onNewResult(dataSource: DataSource<CloseableReference<PooledByteBuffer>>) {
                        if (dataSource.isFinished) {
                            try {
                                val buffer = dataSource.result!!.get()
                                val imgData = ByteArray(buffer.size())
                                buffer.read(0, imgData, 0, imgData.size)
                                val bitmap = BitmapFactory.decodeByteArray(imgData, 0, imgData.size)
                                if (bitmap == null) {
                                    onOpenLinkOnError(url)
                                } else {
                                    imageView.imageBitmap = bitmap
                                }
                            } catch (t: Throwable) {
                                onOpenLinkOnError(url)
                            } finally {
                                dataSource.close()
                            }
                        }
                    }

                    override fun onFailure(dataSource: DataSource<CloseableReference<PooledByteBuffer>>?) {
                        onOpenLinkOnError(url)
                        dataSource?.close()
                    }

                    override fun onCancellation(dataSource: DataSource<CloseableReference<PooledByteBuffer>>?) {}
                    override fun onProgressUpdate(dataSource: DataSource<CloseableReference<PooledByteBuffer>>?) {}
                }, CallerThreadExecutor.getInstance())
            }
        } finally {
            dataSource?.close()
        }
    }

    private fun onOpenLinkOnError(url: String) {
        startLinkActivity(url)
        finish()
    }
}