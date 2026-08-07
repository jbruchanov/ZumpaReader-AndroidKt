package com.scurab.android.zumpareader.ui.image

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.collectWhileStarted
import com.scurab.android.zumpareader.util.startLinkActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by Scurab on 08/09/2017.
 */
private const val kUrl = "Url"

class ImageActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context, url: String): Intent {
            return Intent(context, ImageActivity::class.java).apply {
                putExtra(kUrl, url)
            }
        }
    }

    private val viewModel: ImageViewModel by viewModel()
    private val url: String by lazy { requireNotNull(intent.getStringExtra(kUrl)) { "Null intent.getStringExtra(kUrl)" } }
    private lateinit var imageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_image)
        imageView = findViewById(R.id.image)
        imageView.setOnTouchListener(ImageMatrixTouchHandler(this))

        viewModel.uiState.collectWhileStarted(this) { render(it) }
        viewModel.start(url)
    }

    private fun render(state: ImageUiState) {
        when (state) {
            is ImageUiState.Loading -> Unit
            is ImageUiState.Loaded -> imageView.setImageBitmap(state.bitmap)
            is ImageUiState.Failed -> {
                startLinkActivity(state.url)
                finish()
            }
        }
    }
}
