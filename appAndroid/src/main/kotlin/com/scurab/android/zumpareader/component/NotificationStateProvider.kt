package com.scurab.android.zumpareader.component

import android.content.Context
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
        //the global switch being off still leaves a channel with an importance of its own, which
        //is why both are asked about rather than just the one
        return if (hasNotificationsPermissionGranted()) {
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
