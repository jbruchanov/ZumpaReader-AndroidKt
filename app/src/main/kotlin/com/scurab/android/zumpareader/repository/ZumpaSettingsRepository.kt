package com.scurab.android.zumpareader.repository

import android.content.SharedPreferences
import com.scurab.android.zumpareader.util.ZumpaPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Makes the reactive half of [ZumpaPrefs] observable, so screens stop re-reading preferences in
 * `onResume` to notice that the offline switch or the login state changed.
 *
 * [ZumpaPrefs] itself is untouched - it is still what the settings screen writes to, and this
 * repository picks those writes up through the shared preference listener.
 */
class ZumpaSettingsRepository(private val prefs: ZumpaPrefs) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isOffline: StateFlow<Boolean> = flowOf(ZumpaPrefs.KEY_OFFLINE) { prefs.isOffline }

    val isLoggedIn: StateFlow<Boolean> = flowOf(ZumpaPrefs.KEY_IS_LOGGED_IN) { prefs.isLoggedIn }

    val filter: StateFlow<String> =
        flowOf(ZumpaPrefs.KEY_FILTER, ZumpaPrefs.KEY_IS_LOGGED_IN) { prefs.filter }

    val loadImages: StateFlow<Boolean> = flowOf(ZumpaPrefs.KEY_LOAD_IMAGES) { prefs.loadImages }

    val showLastAuthor: StateFlow<Boolean> =
        flowOf(ZumpaPrefs.KEY_SHOW_LAST_AUTHOR) { prefs.showLastAuthor }

    val loggedUserName: StateFlow<String?> =
        flowOf(ZumpaPrefs.KEY_USER_NAME, ZumpaPrefs.KEY_IS_LOGGED_IN) { prefs.loggedUserName }

    /** The gate on everything that posts - the api rejects it offline and without a session. */
    val isLoggedInNotOffline: StateFlow<Boolean> =
        combine(isLoggedIn, isOffline) { loggedIn, offline -> loggedIn && !offline }
            .stateIn(scope, SharingStarted.Eagerly, prefs.isLoggedInNotOffline)

    val userName: StateFlow<String> = flowOf(ZumpaPrefs.KEY_USER_NAME) { prefs.userName }
    val password: StateFlow<String> = flowOf(ZumpaPrefs.KEY_PASSWORD) { prefs.password }
    val nick: StateFlow<String> = flowOf(ZumpaPrefs.KEY_NICK_NAME) { prefs.nickName }

    val nickName: String get() = prefs.nickName
    val userId: String? get() = prefs.userId

    fun setOffline(value: Boolean) {
        prefs.isOffline = value
    }

    //the settings screen is the only writer for these
    fun setUserName(value: String) = prefs.setUserName(value)

    fun setPassword(value: String) = prefs.setPassword(value)

    fun setNick(value: String) = prefs.setNick(value)

    fun setFilter(value: String) {
        prefs.filter = value
    }

    fun setLoadImages(value: Boolean) = prefs.setLoadImages(value)

    fun setShowLastAuthor(value: Boolean) = prefs.setShowLastAuthor(value)

    private fun <T> flowOf(vararg keys: String, read: () -> T): StateFlow<T> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                //a null key means "everything changed", e.g. after a clear()
                if (key == null || key in keys) {
                    trySend(read())
                }
            }
            prefs.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                prefs.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, read())
    }
}
