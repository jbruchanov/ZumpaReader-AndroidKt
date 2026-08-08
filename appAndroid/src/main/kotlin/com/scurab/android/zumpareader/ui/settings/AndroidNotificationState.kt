package com.scurab.android.zumpareader.ui.settings

import android.content.Context
import android.os.Build
import com.scurab.android.zumpareader.AppConfig
import com.scurab.android.zumpareader.component.NotificationStateProvider

/**
 * [NotificationState] over the platform, so [SettingsViewModel] needs no android import for it.
 */
class AndroidNotificationState(context: Context) : NotificationState {

    private val provider = NotificationStateProvider(context)

    override fun areEnabled(): Boolean =
        provider.areNotificationsEnabled(AppConfig.NotificationChannel.Notifications)

    /** Below Tiramisu there is no runtime permission to ask for. */
    override fun canRequestPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
