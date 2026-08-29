package com.scurab.android.zumpareader.usecase

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import com.scurab.android.zumpareader.AppConfig
import com.scurab.android.zumpareader.R

class CreateNotificationChannelsUseCase(
    private val notificationManager: NotificationManager,
    private val resources: Resources
) {
    constructor(context: Context) : this(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager, context.resources)

    operator fun invoke() {
        //the id changed, so the one it replaced would otherwise sit in the system settings forever
        //as a second, dead entry the user could still toggle
        notificationManager.deleteNotificationChannel(AppConfig.NotificationChannel.LegacyNotifications)

        if (notificationManager.getNotificationChannel(AppConfig.NotificationChannel.Notifications) == null) {
            val channel = NotificationChannel(
                AppConfig.NotificationChannel.Notifications,
                resources.getString(R.string.notifications),
                //DEFAULT, so a reply arrives with the usual notification sound. The channel this
                //replaced was IMPORTANCE_LOW, which made every push silent and - importance being
                //fixed once a channel exists - could not be raised without a new id.
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.lightColor = Color.TRANSPARENT
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(false)
            //off deliberately, and not just left to the default: DEFAULT importance vibrates unless
            //told otherwise, and vibrating needs `android.permission.VIBRATE`, which this app does
            //not ask for. A sound is enough to notice a reply by.
            channel.enableVibration(false)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
