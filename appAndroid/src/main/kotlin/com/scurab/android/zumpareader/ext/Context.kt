package com.scurab.android.zumpareader.ext

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences

val Context.notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

/**
 * What `PreferenceManager.getDefaultSharedPreferences` answered with, spelled out. That class is
 * deprecated and this is the only thing the app ever wanted from it.
 *
 * The name and the mode are copied from the platform rather than chosen:
 * `getDefaultSharedPreferencesName` is `packageName + "_preferences"` and
 * `getDefaultSharedPreferencesMode` is `MODE_PRIVATE`. They have to stay exactly those. Every
 * install already has that file and it is where the credentials, the cookies, the read states and
 * every setting live - a different name here would not fail, it would quietly hand each existing
 * user an empty app.
 */
val Context.defaultSharedPreferences: SharedPreferences
    get() = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
