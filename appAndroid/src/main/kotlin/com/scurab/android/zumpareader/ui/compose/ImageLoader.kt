package com.scurab.android.zumpareader.ui.compose

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/**
 * Coil over the app's own [OkHttpClient], so image requests carry the same cookie jar and timeouts
 * as everything else - which is what `PicassoHttpDownloader2` existed to arrange by hand.
 */
fun buildImageLoader(context: Context, client: OkHttpClient): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }
        .build()
}
