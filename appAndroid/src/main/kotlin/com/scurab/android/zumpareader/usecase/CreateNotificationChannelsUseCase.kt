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
        if (notificationManager.getNotificationChannel(AppConfig.NotificationChannel.Notifications) == null) {
            val channel = NotificationChannel(
                AppConfig.NotificationChannel.Notifications,
                resources.getString(R.string.notifications),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.lightColor = Color.TRANSPARENT
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(false)
            channel.enableVibration(false)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
