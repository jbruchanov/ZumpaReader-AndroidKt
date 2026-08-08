package com.scurab.android.zumpareader.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What the jvm target gets instead of `SharedPreferences`.
 *
 * There is no desktop app yet, so nothing persists: this exists so the jvm target has something to
 * link against, which is what makes the target able to prove that `commonMain` is Android-free.
 * A real desktop build would swap in a file-backed implementation and nothing above would change.
 */
class InMemoryKeyValueStore : KeyValueStore {

    private val values = mutableMapOf<String, Any?>()
    private val _changes = MutableSharedFlow<String?>(extraBufferCapacity = 64)

    override val changes: Flow<String?> = _changes.asSharedFlow()

    override fun getString(key: String, default: String?): String? = values[key] as? String ?: default

    override fun putString(key: String, value: String?) = put(key, value)

    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) = put(key, value)

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String>? = values[key] as? Set<String>

    override fun putStringSet(key: String, value: Set<String>?) = put(key, value)

    private fun put(key: String, value: Any?) {
        values[key] = value
        _changes.tryEmit(key)
    }
}
