package com.scurab.android.zumpareader.usecase

import androidx.core.app.NotificationManagerCompat
import com.scurab.android.zumpareader.AppConfig
import com.scurab.android.zumpareader.component.NotificationStateProvider
import com.scurab.android.zumpareader.repository.AnalyticsReporter
import com.scurab.android.zumpareader.repository.AnalyticsUserProperty
import com.scurab.android.zumpareader.repository.CrashReporter
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What the Android app does at launch. Called from `ZumpaReaderApp.onCreate`, which is now the only
 * thing it says about startup.
 *
 * `FirebaseApp.initializeApp` is deliberately not among the chores: the google-services plugin
 * merges in `FirebaseInitProvider`, and a ContentProvider is created before `Application.onCreate`,
 * so Firebase is already up by the time anything here runs.
 */
class AndroidInitAppUseCase(
    private val createNotificationChannels: CreateNotificationChannelsUseCase,
    private val settings: ZumpaSettingsRepository,
    private val crashReporter: CrashReporter,
    private val notifications: NotificationStateProvider,
    private val analytics: AnalyticsReporter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : InitAppUseCase {

    override fun invoke() {
        createNotificationChannels()
        reportCrashesAsTheSignedInUser()
        //after the channels and not before: on a first run there is no channel to read an
        //importance off until the line above has made one
        reportHowNotificationsAreSetUp()
    }

    /**
     * Puts the signed-in user name on every crash report, so a report can be tied to the person who
     * filed it, and the anonymous per-install id there before and after a session, so one
     * anonymous user's reports still group together.
     *
     * Driven off [ZumpaSettingsRepository.loggedUserName] rather than called from `AuthRepository`:
     * that flow is keyed on the login flag *and* the stored name, it is a `StateFlow` that replays
     * the current value to a new collector, and it is `distinctUntilChanged`. So one collector
     * covers all three moments - launching an already signed-in session, a login, and a logout -
     * and `:shared` keeps knowing nothing about Crashlytics.
     *
     * The scope is the process: the collector is meant to outlive every screen, and there is
     * nothing after `Application` to cancel it.
     */
    private fun reportCrashesAsTheSignedInUser() {
        scope.launch {
            settings.loggedUserName.collect { crashReporter.setUserId(settings.userId) }
        }
    }

    /**
     * Whether this install can be notified at all, and how loudly, as two user properties rather
     * than one - the permission and the channel are separate settings that produce the same
     * symptom, and telling a blocked app from a quietened one is the point of asking.
     *
     * Without these, every push number the app reports is uninterpretable: an install that is never
     * notified because the user said no is not evidence of anything being broken, and it would
     * otherwise be counted beside one that is.
     *
     * Read at launch and not watched. A property is sticky, so this is as current as the last cold
     * start - a permission revoked mid-session is not seen until the next one, which is accurate
     * enough for something meant to segment by rather than to count.
     */
    private fun reportHowNotificationsAreSetUp() {
        analytics.setUserProperty(
            AnalyticsUserProperty.NotificationsEnabled,
            notifications.hasNotificationsPermissionGranted().toString(),
        )
        analytics.setUserProperty(
            AnalyticsUserProperty.ChannelImportance,
            notifications
                .channelImportance(AppConfig.NotificationChannel.Notifications)
                .asImportanceName(),
        )
    }
}

/**
 * The importance as a word, because a console groups by value and `3` is not something a query is
 * readable with. `missing` is its own answer rather than `none`: no channel and a channel the user
 * has switched off are different states, and only one of them is the app's fault.
 */
private fun Int?.asImportanceName(): String = when (this) {
    null -> "missing"
    NotificationManagerCompat.IMPORTANCE_NONE -> "none"
    NotificationManagerCompat.IMPORTANCE_MIN -> "min"
    NotificationManagerCompat.IMPORTANCE_LOW -> "low"
    NotificationManagerCompat.IMPORTANCE_DEFAULT -> "default"
    NotificationManagerCompat.IMPORTANCE_HIGH -> "high"
    NotificationManagerCompat.IMPORTANCE_MAX -> "max"
    else -> "unknown"
}
