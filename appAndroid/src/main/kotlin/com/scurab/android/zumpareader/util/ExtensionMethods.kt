package com.scurab.android.zumpareader.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
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

fun Context.getRandomCameraFileUri(): String {
    val path = File(applicationContext.filesDir.absolutePath, "Pictures" /*file_paths.xml Path */)
    if (!path.exists()) {
        path.mkdir()
    }
    val file = File(path, "camera_%s.jpg".format(System.currentTimeMillis()))
    return file.absolutePath
}

fun Context.saveToClipboard(text: String?) {
    val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clip.setPrimaryClip(ClipData.newPlainText(text, text))
}

//looksLikeImageUrl moved to the shared module - the offline download classifies urls too

fun Context.startLinkActivity(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = url.toUri()
        startActivity(intent)
    } catch (e: Throwable) {
        e.printStackTrace()
        toast(R.string.unable_to_finish_operation)
    }
}
