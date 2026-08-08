package com.scurab.android.zumpareader.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [KeyValueStore] over the default `SharedPreferences` - the same file the app has always written
 * to, so nothing needs migrating.
 */
class SharedPreferencesStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences =
        @Suppress("DEPRECATION") PreferenceManager.getDefaultSharedPreferences(context)

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getStringSet(key: String): Set<String>? = prefs.getStringSet(key, null)

    override fun putStringSet(key: String, value: Set<String>?) {
        prefs.edit().putStringSet(key, value).apply()
    }

    override val changes: Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> trySend(key) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
