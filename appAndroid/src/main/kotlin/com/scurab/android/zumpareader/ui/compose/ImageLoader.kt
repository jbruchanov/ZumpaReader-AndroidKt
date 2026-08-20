package com.scurab.android.zumpareader.ui.compose

import android.content.Context
import coil3.ImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient

/**
 * Coil over the app`s own Ktor client, so image requests carry the same cookie jar as everything
 * else - which is what `PicassoHttpDownloader2` existed to arrange by hand.
 *
 * [client] is the image client of
 * [com.scurab.android.zumpareader.data.buildImageHttpClient], not the api one: it shares the cookie
 * jar but follows redirects and does not bust caches. See that function for why the two differ.
 */
fun buildImageLoader(context: Context, client: HttpClient): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(KtorNetworkFetcherFactory(httpClient = client))
        }
        .build()
}
