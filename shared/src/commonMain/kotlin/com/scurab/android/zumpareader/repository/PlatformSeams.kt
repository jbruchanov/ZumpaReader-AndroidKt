package com.scurab.android.zumpareader.repository

/**
 * The two things the shared repositories need from the platform that have no common implementation.
 *
 * Interfaces rather than `expect`/`actual` on purpose: these have to be substitutable in a test as
 * well as per platform, and an `expect class` is neither.
 */

/**
 * A push token. Firebase on Android; nothing anywhere else yet, so the jvm implementation returns
 * null and [AuthRepository] reports "logged in but not registered for push", which is a state it
 * already handles.
 */
interface PushTokenProvider {
    suspend fun token(): String?
}

/** Warms an image cache. Coil on Android - see the offline download. */
interface ImagePrefetcher {
    suspend fun prefetch(url: String)
}

/** A [PushTokenProvider] for platforms with no push at all. */
object NoPushTokenProvider : PushTokenProvider {
    override suspend fun token(): String? = null
}

/** An [ImagePrefetcher] for platforms with no image cache to warm. */
object NoImagePrefetcher : ImagePrefetcher {
    override suspend fun prefetch(url: String) = Unit
}
