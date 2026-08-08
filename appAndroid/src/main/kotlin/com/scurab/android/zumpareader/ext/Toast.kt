package com.scurab.android.zumpareader.ext

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

fun Context.toast(@StringRes msgRes: Int) {
    toast(resources.getString(msgRes))
}

fun Context.toast(msg: String?) {
    if (msg != null) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
