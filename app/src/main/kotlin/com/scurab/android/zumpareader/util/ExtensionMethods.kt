package com.scurab.android.zumpareader.util

import android.content.Context
import android.support.annotation.IdRes
import android.support.annotation.StringRes
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.View
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
    return URLEncoder.encode(this, "utf-8")
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
