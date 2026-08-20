package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.ZumpaOfflineApi
import com.scurab.android.zumpareader.data.toDto
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

/**
 * The snapshot used to be read once, in `Application.onCreate`, and only when offline mode was
 * already on - so switching into offline mode during a session left the store empty and the list
 * blank however good the file on disk was. These pin the lazy read that replaced it.
 */
class OfflineDataRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun snapshotOf(vararg ids: String): String {
        val threads = ids.associateWith { id ->
            ZumpaThread(id, "subject $id").apply {
                offlineItems = listOf(ZumpaThreadItem("author", "body $id", 1L))
            }.toDto()
        }
        return json.encodeToString(threads)
    }

    private fun repository(path: String, api: ZumpaOfflineApi) =
        OfflineDataRepository(path, api, json)

    @Test
    fun `reads the snapshot into the offline api`() = runTest {
        val file = createTempFile("offline", ".json").apply { writeText(snapshotOf("1", "2")) }
        val api = ZumpaOfflineApi(LinkedHashMap())

        repository(file.toString(), api).ensureLoaded()

        assertEquals(setOf("1", "2"), api.getMainPage("0").items.keys)
        assertEquals(listOf("body 1"), api.getThreadPage("1", "1").items.map { it.body })
    }

    /** The whole point of the fix: no offline flag is consulted, so a toggle picks the file up. */
    @Test
    fun `reads it whatever the offline setting was at startup`() = runTest {
        val file = createTempFile("offline", ".json").apply { writeText(snapshotOf("7")) }
        val api = ZumpaOfflineApi(LinkedHashMap())
        val repository = repository(file.toString(), api)

        assertEquals(0, api.getMainPage("0").items.size)
        repository.ensureLoaded()
        assertEquals(setOf("7"), api.getMainPage("0").items.keys)
    }

    @Test
    fun `reads the file once however often it is asked`() = runTest {
        val file = createTempFile("offline", ".json").apply { writeText(snapshotOf("1")) }
        val api = ZumpaOfflineApi(LinkedHashMap())
        val repository = repository(file.toString(), api)

        repository.ensureLoaded()
        file.writeText(snapshotOf("9"))
        repository.ensureLoaded()

        assertEquals(setOf("1"), api.getMainPage("0").items.keys)
    }

    /** A fresh download is newer than the file - a later reload must not put the old data back. */
    @Test
    fun `data set by a download survives a later ensureLoaded`() = runTest {
        val file = createTempFile("offline", ".json").apply { writeText(snapshotOf("old")) }
        val api = ZumpaOfflineApi(LinkedHashMap())
        val repository = repository(file.toString(), api)

        repository.setData(linkedMapOf("new" to ZumpaThread("new", "fresh")))
        repository.ensureLoaded()

        assertEquals(setOf("new"), api.getMainPage("0").items.keys)
    }

    @Test
    fun `a missing snapshot is not an error`() = runTest {
        val api = ZumpaOfflineApi(LinkedHashMap())
        repository("/no/such/file.json", api).ensureLoaded()
        assertEquals(0, api.getMainPage("0").items.size)
    }
}
