package com.scurab.android.zumpareader.util

import kotlinx.coroutines.flow.Flow

/**
 * The bit of `SharedPreferences` the app actually uses, as an interface, so [ZumpaPrefs] and
 * everything that reads settings can live in common code.
 *
 * [changes] is not a convenience: `ZumpaSettingsRepository` is built on the fact that a write is
 * observable, which used to mean an `OnSharedPreferenceChangeListener` and therefore an Android
 * import in a repository.
 */
interface KeyValueStore {

    fun getString(key: String, default: String?): String?

    fun putString(key: String, value: String?)

    fun getBoolean(key: String, default: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)

    fun getStringSet(key: String): Set<String>?

    fun putStringSet(key: String, value: Set<String>?)

    /** The key that changed. `null` means "assume everything did", as after a clear. */
    val changes: Flow<String?>
}
