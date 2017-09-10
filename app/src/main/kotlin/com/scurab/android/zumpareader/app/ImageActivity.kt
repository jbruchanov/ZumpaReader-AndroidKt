package com.scurab.android.zumpareader.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.ImageView
import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.BaseDataSubscriber
import com.facebook.datasource.DataSource
import com.facebook.datasource.DataSources
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.image.CloseableBitmap
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.request.ImageRequest
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
        loadImage(url)
    }

    private fun loadImage(url: String) {
        val imagePipeline = Fresco.getImagePipeline()
        var dataSource = imagePipeline.fetchDecodedImage(scaledImageRequest(url, this), this)
        try {
            val result = DataSources.waitForFinalResult(dataSource)
            if (result != null) {
                dataSource.subscribe(object : BaseDataSubscriber<CloseableReference<CloseableImage>>() {
                    override fun onNewResultImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) {
                        if (!dataSource.isFinished) {
                            return
                        }
                        val closeableImageRef = dataSource.result
                        (closeableImageRef?.get() as? CloseableBitmap)?.let {
                            imageView.post {
                                imageView.imageBitmap = it.underlyingBitmap
                            }
                        }
                        dataSource.close()
                    }

                    override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>?) {
                        startLinkActivity(url)
                        finish()
                        dataSource?.close()
                    }

                }, CallerThreadExecutor.getInstance())
            }
        } finally {
            dataSource?.close()
        }
    }
}