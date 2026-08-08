package com.scurab.android.zumpareader.component

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

class NotificationStateProvider(private val notificationManager: NotificationManagerCompat) {

    constructor(context: Context) : this(NotificationManagerCompat.from(context))

    fun hasNotificationsPermissionGranted() = notificationManager.areNotificationsEnabled()
    fun areAllNotificationsEnabled() =
        hasNotificationsPermissionGranted() &&
            notificationManager.notificationChannelsCompat.all { it.importance != NotificationManagerCompat.IMPORTANCE_NONE }

    fun areNotificationsEnabled(vararg channels: String): Boolean {
        return hasNotificationsPermissionGranted() && channels.all { isChannelEnabled(it) }
    }

    fun isChannelEnabled(channel: String): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O/*A8, API26*/) {
            true
            //if global switch is turned off, then you can still have channel having the importance (aka is turned no)
        } else if (hasNotificationsPermissionGranted()) {
            val channelImportance = notificationManager.notificationChannelsCompat
                .firstOrNull { it.id == channel }
                ?.importance
                ?: NotificationManagerCompat.IMPORTANCE_NONE
            channelImportance != NotificationManagerCompat.IMPORTANCE_NONE
        } else {
            false
        }
    }
}
