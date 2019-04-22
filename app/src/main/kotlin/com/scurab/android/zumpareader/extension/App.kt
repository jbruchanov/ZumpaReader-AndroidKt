package com.scurab.android.zumpareader.extension

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Looper
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.scurab.android.zumpareader.BuildConfig
import com.scurab.android.zumpareader.ZumpaReaderApp

/**
 * Created by jbruchanov on 16/10/2017.
 */

/**
 * Get application as typed object
 */
fun Context.app(): ZumpaReaderApp = this.applicationContext as ZumpaReaderApp
fun Fragment.app(): ZumpaReaderApp = this.requireContext().app()

/**
 * Load asset as string
 */
fun Context.loadAsset(file: String): String {
    val stream = assets.open(file)
    stream.use {
        return stream.reader().readText()
    }
}

/**
 * Start fragment transaction
 */
inline fun FragmentManager.transaction(crossinline op: FragmentTransaction.() -> Unit) {
    val transaction = this.beginTransaction()
    op(transaction)
    transaction.commitAllowingStateLoss()
}

fun Context.hideKeyboard() {
    hideKeyboard(null)
}

fun Context.hideKeyboard(view: View?) {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.let {
        val focused = view?.findFocus()
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
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.let {
        view?.requestFocus()
        if (!imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)) {
            imm.showSoftInput(view, 0)
        }
    }
}

fun Context.isLandscape(): Boolean = resources.displayMetrics.let { it.widthPixels > it.heightPixels }

/**
 * Run specific function on specific or newer Android version
 */
inline fun atLeastAndroidVersion(version: Int, onlyForDebug: Boolean = false, func: () -> Unit) {
    if (Build.VERSION.SDK_INT >= version
            && (!onlyForDebug || BuildConfig.DEBUG)) {
        func()
    }
}

fun Activity.postDelayed(delay: Long, runnable: () -> Unit) {
    window.decorView.postDelayed(runnable, delay)
}

fun <T> List<T>.last(offset: Int) = this[size - 1 - offset]
fun <T> List<T>.lastOrNull(offset: Int): T? {
    val index = size - 1 - offset
    return if (index >= 0 && index - 1 < size) this[index] else null
}

fun assertMainThread(errorMsg: (() -> String)? = null) {
    if (Looper.getMainLooper() != Looper.myLooper()) {
        val msg = errorMsg?.invoke() ?: "Non-main thread access detected, thread:${Thread.currentThread().name}!"
        throw IllegalStateException(msg)
    }
}