package com.scurab.android.zumpareader.ui.compose

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient

/**
 * Coil over the app`s own Ktor client, so image requests carry the same cookie jar as everything
 * else - which is what `PicassoHttpDownloader2` existed to arrange by hand.
 *
 * [client] is the image client of
 * [com.scurab.android.zumpareader.data.buildImageHttpClient], not the api one: it shares the cookie
 * jar but follows redirects and does not bust caches. See that function for why the two differ.
 *
 * The gif decoder is added by hand because coil ships none by default: without it an animated gif
 * still loads and still draws, as the single frame the platform bitmap decoder returns - which is
 * what the forum's gifs have been doing all along, silently. `.gif` is in `Urls.IMAGE_EXTENSIONS`,
 * so they arrive here as inline images like anything else.
 *
 * Nothing else has to change for them to move: the thread row draws through an `AsyncImagePainter`,
 * which animates whatever drawable it is handed. The full-screen viewer does not - it needs a real
 * `Bitmap` for the zoom gesture, so tapping an animating gif still freezes it. Deliberate.
 */
//KtorNetworkFetcherFactory is coil's experimental api - the ktor3 integration is the whole
//reason the app has one http stack instead of two, so it is opted into rather than avoided
@OptIn(ExperimentalCoilApi::class)
fun buildImageLoader(context: Context, client: HttpClient): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(KtorNetworkFetcherFactory(httpClient = client))
            //ImageDecoder is api 28 and minSdk is 26, so the older two versions get coil's own
            //decoder. Same result, more work per frame - and it is the only gif support they can have.
            add(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    AnimatedImageDecoder.Factory()
                } else {
                    GifDecoder.Factory()
                }
            )
        }
        .build()
}
