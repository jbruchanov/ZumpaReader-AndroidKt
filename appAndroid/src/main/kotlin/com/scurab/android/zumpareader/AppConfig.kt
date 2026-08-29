package com.scurab.android.zumpareader

object AppConfig {
    object NotificationChannel {
        /**
         * Defined in `build.gradle.kts`, which also writes it into
         * `R.string.default_notification_channel_id` for the manifest - so the id the app creates
         * and the id firebase is told about cannot drift apart. They had.
         */
        const val Notifications = BuildConfig.NotificationChannelId

        /**
         * The id before [Notifications], deleted at startup by
         * [com.scurab.android.zumpareader.usecase.CreateNotificationChannelsUseCase].
         *
         * It was created `IMPORTANCE_LOW` with vibration off, which is why push notifications made
         * no sound - a channel cannot be changed once it exists, so an install that had it kept it.
         */
        const val LegacyNotifications = "notifications"
    }
}
