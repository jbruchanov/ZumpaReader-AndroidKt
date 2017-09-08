package com.scurab.android.zumpareader.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.scurab.android.zumpareader.R
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
        imageView = findViewById<ImageView>(R.id.image)
        imageView.setOnTouchListener(ImageMatrixTouchHandler(this))
        loadImage(url)
    }

    private fun loadImage(url: String) {
        val imageRequest = ImageRequestBuilder
                .newBuilderWithSource(Uri.parse(url))
                .build()

        val imagePipeline = Fresco.getImagePipeline()
        val dataSource = imagePipeline.fetchDecodedImage(imageRequest, this)
        dataSource.subscribe(object : BaseBitmapDataSubscriber() {
            override fun onNewResultImpl(bitmap: Bitmap?) {
                if (dataSource.isFinished && bitmap != null) {
                    imageView.post {
                        imageView.imageBitmap = bitmap
                    }
                }
            }

            override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>?) {
                startLinkActivity(url)
                finish()
                dataSource?.close()
            }

        }, CallerThreadExecutor.getInstance())
    }
}