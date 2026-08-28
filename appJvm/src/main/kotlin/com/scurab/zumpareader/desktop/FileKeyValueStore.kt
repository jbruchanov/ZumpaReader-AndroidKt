package com.scurab.zumpareader.desktop

import com.scurab.android.zumpareader.util.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.util.Properties

/**
 * What the desktop app gets instead of `SharedPreferences`.
 *
 * `InMemoryKeyValueStore` in `shared/jvmMain` exists so the jvm target has something to link
 * against, and says in as many words that a real desktop build would want a file-backed one. This
 * is that: a session survives being closed, which is the difference between offering a login and
 * offering one that has to be repeated every launch.
 *
 * A `Properties` file rather than anything cleverer, because the whole of [KeyValueStore] is
 * strings, booleans and small string sets. Sets are joined on [SET_SEPARATOR] - a newline, which
 * no value here can contain: they are cookies and thread ids.
 */
class FileKeyValueStore(private val file: File) : KeyValueStore {

    private val properties = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }
    private val _changes = MutableSharedFlow<String?>(extraBufferCapacity = 64)

    override val changes: Flow<String?> = _changes.asSharedFlow()

    override fun getString(key: String, default: String?): String? =
        properties.getProperty(key) ?: default

    override fun putString(key: String, value: String?) = put(key, value)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        properties.getProperty(key)?.toBooleanStrictOrNull() ?: default

    override fun putBoolean(key: String, value: Boolean) = put(key, value.toString())

    override fun getStringSet(key: String): Set<String>? =
        properties.getProperty(key)?.split(SET_SEPARATOR)?.filter { it.isNotEmpty() }?.toSet()

    override fun putStringSet(key: String, value: Set<String>?) =
        put(key, value?.joinToString(SET_SEPARATOR))

    private fun put(key: String, value: String?) {
        if (value == null) properties.remove(key) else properties.setProperty(key, value)
        file.parentFile?.mkdirs()
        //written through on every put: there is no lifecycle here to flush on, and the file is
        //a few hundred bytes
        file.outputStream().use { properties.store(it, null) }
        _changes.tryEmit(key)
    }

    private companion object {
        const val SET_SEPARATOR = "\n"
    }
}
