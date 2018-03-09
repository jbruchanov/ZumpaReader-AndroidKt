package com.scurab.android.zumpareader.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.annotation.*
import android.support.v4.app.Fragment
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZR
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.util.*

/**
 * Created by JBruchanov on 25/11/2015.
 */

@Suppress("UNCHECKED_CAST")
fun <T : View> RecyclerView.ViewHolder.find(@IdRes resId: Int): T {
    return itemView.findViewById<View>(resId) as T? ?: throw NullPointerException("Unable to find view with id:'%s'".format(resId))
}

inline fun <T> T?.exec(f: (T) -> Unit) {
    if (this != null) {
        f(this)
    }
}

fun <T> T?.ifNull(f: () -> Unit) {
    if (this == null) {
        f()
    }
}

fun String.encodeHttp(): String {
    return URLEncoder.encode(this, ZR.Constants.ENCODING)
}

fun <K, V> Map<K, V>.asListOfValues(): ArrayList<V> = ArrayList(this.values)
fun <K, V> Map<K, V>.asListOfKeys(): ArrayList<K> = ArrayList(this.keys)


private val typedValue = TypedValue()
fun Context.obtainStyledColor(attr: Int): Int {
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

fun Context.toast(@StringRes msgRes: Int) {
    toast(resources.getString(msgRes))
}

fun Context.toast(msg: String?) {
    if (msg != null) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

fun Drawable.wrapWithTint(color: Int): Drawable {
    var drawable = DrawableCompat.wrap(this)
    DrawableCompat.setTintList(drawable, ColorStateList.valueOf(color))
    return drawable
}

fun List<Fragment?>.lastNonNullFragment(): Fragment? {
    for (i in Math.max(0, size - 1) downTo 0 step 1) {
        if (this[i] != null) {
            return this[i]
        }
    }
    return null
}

fun Context.hideKeyboard() {
    hideKeyboard(null)
}

fun Context.hideKeyboard(view: View?) {
    var imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.exec {
        var focused = view?.findFocus() ?: null
        if (focused == null) {
            imm.hideSoftInputFromInputMethod(null, 0)
        } else {
            if (!imm.hideSoftInputFromWindow(focused.windowToken, InputMethodManager.HIDE_IMPLICIT_ONLY)) {
                imm.hideSoftInputFromWindow(focused.windowToken, 0)
            }
        }
    }
}

fun Context.showKeyboard() {
    showKeyboard()
}

fun Context.showKeyboard(view: View?) {
    var imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.exec {
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        view?.requestFocus()
    }
}

fun Context.post(runnable: Runnable) {
    Handler(Looper.getMainLooper()).post(runnable)
}

fun Context.post(runnable: () -> Unit) {
    Handler(Looper.getMainLooper()).post(runnable)
}

fun Context.postDelayed(runnable: Runnable, delay: Long) {
    if (delay <= 0) {
        post(runnable)
    } else {
        Handler(Looper.getMainLooper()).postDelayed(runnable, delay)
    }
}

fun Context.getRandomCameraFileUri(withScheme: Boolean = false): String {
    val path = File(applicationContext.filesDir.absolutePath, "Pictures" /*file_paths.xml Path */)
    if (!path.exists()) {
        path.mkdir()
    }
    var file = File(path, "camera_%s.jpg".format(System.currentTimeMillis()))
    return if (withScheme) "file://" + file.absolutePath else file.absolutePath
}

fun ImageView.setImageTint(color: Int) {
    if (drawable != null) {
        setImageDrawable(drawable.wrapWithTint(color))
    }
}

fun ImageButton.setImageTint(color: Int) {
    if (drawable != null) {
        setImageDrawable(drawable.wrapWithTint(color))
    }
}

fun Boolean.asVisibility(falseValue: Int = View.GONE): Int {
    return if (this) View.VISIBLE else falseValue
}

fun Context.saveToClipboard(text: String?) {
    var clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clip.primaryClip = ClipData.newPlainText(text, text)
}

fun Context.saveToClipboard(uri: Uri) {
    var clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clip.primaryClip = ClipData.newRawUri(uri.toString(), uri)
}

fun View.setPadding(px: Int) {
    setPadding(px, px, px, px)
}

fun View.setPaddingRes(@DimenRes dimenRes: Int) {
    val px = resources.getDimensionPixelSize(dimenRes)
    setPadding(px, px, px, px)
}

fun Uri.isImage(): Boolean {
    val path = path.toLowerCase()
    return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".bmp") || path.endsWith(".gif")
}

fun String.isImageUri(): Boolean {
    try {
        return Uri.parse(this).isImage()
    } catch(e: Exception) {
        return false
    }
}

fun InputStream.contentAsString(): String {
    var bos = ByteArrayOutputStream()
    copyTo(bos)
    return String(bos.toByteArray())
}

fun ViewTreeObserver.removeGlobalLayoutListenerSafe(listener: ViewTreeObserver.OnGlobalLayoutListener) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        removeOnGlobalLayoutListener(listener)
    } else {
        removeGlobalOnLayoutListener(listener)
    }
}

fun Context.startLinkActivity(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    } catch(e: Throwable) {
        e.printStackTrace()
        toast(R.string.unable_to_finish_operation)
    }
}

fun Context.getColorFromTheme(@AttrRes attrResId: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrResId, typedValue, true)
    return typedValue.data
}