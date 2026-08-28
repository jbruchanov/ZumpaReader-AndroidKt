package com.scurab.android.zumpareader.usecase

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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : InitAppUseCase {

    override fun invoke() {
        createNotificationChannels()
        reportCrashesAsTheSignedInUser()
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
}
