package com.scurab.android.zumpareader.ui.image

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.scurab.android.zumpareader.ui.compose.setZumpaContent

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = requireNotNull(intent.getStringExtra(kUrl)) { "Null intent.getStringExtra(kUrl)" }
        setZumpaContent { ImageScreen(url) }
    }
}
