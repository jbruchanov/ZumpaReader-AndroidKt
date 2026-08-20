package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.ZumpaOfflineApi
import com.scurab.android.zumpareader.data.OfflineThreadDto
import com.scurab.android.zumpareader.data.toDomain
import com.scurab.android.zumpareader.model.ZumpaThread
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
    private val json: Json,
) {

    private var isLoaded = false

    val path: String get() = snapshotPath

    /**
     * Fills the offline api from disk, once.
     *
     * This used to run only in `Application.onCreate`, and only when offline mode was *already* on,
     * which meant switching into offline mode during a session never read the snapshot: the store
     * stayed empty and the list came up blank however good the file on disk was. So it is called
     * from the api factory instead - wherever offline mode is actually used, cold start or toggle -
     * and is a no-op after the first time.
     */
    fun ensureLoaded() {
        if (isLoaded) {
            return
        }
        isLoaded = true
        val file = Path(snapshotPath)
        if (!SystemFileSystem.exists(file)) {
            return
        }
        val text = SystemFileSystem.source(file).buffered().use { it.readString() }
        val dtos = json.decodeFromString<Map<String, OfflineThreadDto>>(text)
        offlineApi.offlineData = LinkedHashMap<String, ZumpaThread>().apply {
            dtos.forEach { (id, dto) -> put(id, dto.toDomain()) }
        }
    }

    /** A fresh download, which is also the newest thing on disk - so no reload can undo it. */
    fun setData(data: LinkedHashMap<String, ZumpaThread>) {
        isLoaded = true
        offlineApi.offlineData = data
    }

    companion object {
        const val OFFLINE_FILE_NAME = "offline.json"
    }
}
