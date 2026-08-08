package com.scurab.android.zumpareader.repository

import android.content.Context
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * The Android half of [ImagePrefetcher]. Warms Coil's **disk** cache only - filling the memory cache
 * with an offline download would be pointless, and that was true of the use case before it moved.
 */
class CoilImagePrefetcher(
    private val context: Context,
    private val imageLoader: ImageLoader,
) : ImagePrefetcher {

    override suspend fun prefetch(url: String) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
        imageLoader.execute(request)
    }
}
