package com.scurab.android.zumpareader.ext

import android.app.NotificationManager
import android.content.Context
import android.preference.PreferenceManager

val Context.notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
val Context.defaultSharedPreferences get() = PreferenceManager.getDefaultSharedPreferences(this)

val Context.layoutInflater get() = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as android.view.LayoutInflater
