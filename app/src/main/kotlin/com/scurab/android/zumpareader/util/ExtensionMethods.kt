package com.scurab.android.zumpareader.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ext.toast
import java.io.File

/**
 * Created by JBruchanov on 25/11/2015.
 */

private val typedValue = TypedValue()

/** Still needed off the compose path - `MyFirebaseService` colours its notifications with it. */
fun Context.obtainStyledColor(attr: Int): Int {
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

fun Context.getRandomCameraFileUri(withScheme: Boolean = false): String {
    val path = File(applicationContext.filesDir.absolutePath, "Pictures" /*file_paths.xml Path */)
    if (!path.exists()) {
        path.mkdir()
    }
    val file = File(path, "camera_%s.jpg".format(System.currentTimeMillis()))
    return if (withScheme) "file://" + file.absolutePath else file.absolutePath
}

fun Context.saveToClipboard(text: String?) {
    val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clip.setPrimaryClip(ClipData.newPlainText(text, text))
}

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

fun Context.startLinkActivity(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    } catch (e: Throwable) {
        e.printStackTrace()
        toast(R.string.unable_to_finish_operation)
    }
}
