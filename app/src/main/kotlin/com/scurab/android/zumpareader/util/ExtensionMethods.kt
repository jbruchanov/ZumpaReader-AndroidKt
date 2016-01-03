package com.scurab.android.zumpareader.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.support.annotation.IdRes
import android.support.annotation.StringRes
import android.support.v4.app.Fragment
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import java.net.URLEncoder
import java.util.*

/**
 * Created by JBruchanov on 25/11/2015.
 */


public fun <T : View> RecyclerView.ViewHolder.find(@IdRes resId: Int): T {
    var t = itemView.findViewById(resId) as T?
    t.execIfNull { throw NullPointerException("Unable to find view with id:'%s'".format(resId)) }
    return t!!
}

public inline fun <T> T?.exec(f: (T) -> Unit) {
    if (this != null) {
        f(this)
    }
}

public inline fun <T> T?.execOn(f: T.() -> Unit) {
    if (this != null) {
        f(this)
    }
}

public fun <T> T?.execIfNull(f: () -> Unit) {
    if (this == null) {
        f()
    }
}

public fun String.encodeHttp(): String {
    return URLEncoder.encode(this, "iso-8859-2")
}

public fun <K, V> Map<K, V>.asListOfValues(): ArrayList<V> = ArrayList(this.values);
public fun <K, V> Map<K, V>.asListOfKeys(): ArrayList<K> = ArrayList(this.keys);


private val typedValue = TypedValue();
public fun Context.obtainStyledColor(attr: Int): Int {
    theme.resolveAttribute(attr, typedValue, true);
    return typedValue.data;
}

public fun Context.toast(@StringRes msgRes: Int) {
    toast(resources.getString(msgRes))
}

public fun Context.toast(msg: String?) {
    if (msg != null) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

public fun Drawable.wrapWithTint(color: Int): Drawable {
    var drawable = DrawableCompat.wrap(this);
    DrawableCompat.setTintList(drawable, ColorStateList.valueOf(color));
    return drawable
}

public fun List<Fragment?>.lastNonNullFragment(): Fragment? {
    for (i in Math.min(0, size - 1) downTo 0 step 1) {
        if (this[i] != null) {
            return this[i]
        }
    }
    return null
}

public fun Context.hideKeyboard() {
    hideKeyboard(null)
}

public fun Context.hideKeyboard(view: View?) {
    var imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;
    imm.exec {
        var focused = view?.findFocus() ?: null;
        if (focused == null) {
            imm.hideSoftInputFromInputMethod(null, 0);
        } else {
            if (!imm.hideSoftInputFromWindow(focused.windowToken, InputMethodManager.HIDE_IMPLICIT_ONLY)) {
                imm.hideSoftInputFromWindow(focused.windowToken, 0);
            }
        }
    }
}

public fun Context.showKeyboard() {
    showKeyboard()
}

public fun Context.showKeyboard(view : View?) {
    var imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;
    imm.exec {
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        view?.requestFocus()
    }
}
