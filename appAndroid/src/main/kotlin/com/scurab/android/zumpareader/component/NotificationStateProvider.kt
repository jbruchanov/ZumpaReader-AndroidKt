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
        return hasNotificationsPermissionGranted() &&
            importanceOf(channel) != NotificationManagerCompat.IMPORTANCE_NONE
    }

    /**
     * How loudly the channel is set to arrive, whatever the global switch says - the two are
     * separate settings and a silent app can be either of them. Null when there is no such channel,
     * which for this app means the startup that creates it has not run.
     *
     * Read rather than assumed because importance is fixed when a channel is created and the user
     * can lower it afterwards, so what the app asked for is not what it necessarily has.
     */
    fun channelImportance(channel: String): Int? =
        notificationManager.notificationChannelsCompat.firstOrNull { it.id == channel }?.importance

    private fun importanceOf(channel: String) =
        channelImportance(channel) ?: NotificationManagerCompat.IMPORTANCE_NONE
}
