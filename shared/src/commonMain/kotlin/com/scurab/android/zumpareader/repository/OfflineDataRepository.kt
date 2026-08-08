package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.ZumpaOfflineApi
import com.scurab.android.zumpareader.data.OfflineThreadDto
import com.scurab.android.zumpareader.data.toDomain
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.util.ZumpaPrefs
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json

/**
 * The offline snapshot on disk and the api that serves it.
 *
 * [snapshotPath] is handed in rather than derived from a `Context`: which directory an app may write
 * to is the one genuinely platform-specific thing here, and it is a string. The file access itself
 * is `kotlinx-io`, so this reads and writes the same on every target.
 */
class OfflineDataRepository(
    private val snapshotPath: String,
    private val offlineApi: ZumpaOfflineApi,
    private val prefs: ZumpaPrefs,
    private val json: Json,
) {

    val path: String get() = snapshotPath

    /** Only reads when offline mode is on, as it always did. */
    fun loadFromDisk() {
        val file = Path(snapshotPath)
        if (!SystemFileSystem.exists(file) || !prefs.isOffline) {
            return
        }
        val text = SystemFileSystem.source(file).buffered().use { it.readString() }
        val dtos = json.decodeFromString<Map<String, OfflineThreadDto>>(text)
        offlineApi.offlineData = LinkedHashMap<String, ZumpaThread>().apply {
            dtos.forEach { (id, dto) -> put(id, dto.toDomain()) }
        }
    }

    fun setData(data: LinkedHashMap<String, ZumpaThread>) {
        offlineApi.offlineData = data
    }

    companion object {
        const val OFFLINE_FILE_NAME = "offline.json"
    }
}
