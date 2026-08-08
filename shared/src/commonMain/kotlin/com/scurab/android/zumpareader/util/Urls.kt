package com.scurab.android.zumpareader.util

private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".bmp", ".gif")

/**
 * Whether a url points at an image, without android.net.Uri, so the code that classifies a url can
 * be unit tested - `Uri.parse` is a stub in a JVM test and used to classify everything as a link.
 * `Uri.getPath` drops the query and the fragment and this strips them by hand; everything else is
 * the same suffix check.
 */
fun String.looksLikeImageUrl(): Boolean {
    val path = substringBefore('#').substringBefore('?').lowercase()
    return IMAGE_EXTENSIONS.any { path.endsWith(it) }
}
