package com.scurab.android.zumpareader.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.scurab.android.zumpareader.model.ZumpaReadState
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Guards the two things that are easy to break silently after the move off gson.
 *
 * **The keys.** Every serialised property carries an explicit `@SerialName`, because the JSON is
 * either persisted or comes off the wire while the property names are the obfuscator's to rename.
 * The assertions here name the keys as literal strings, so a rename shows up as a failing test
 * rather than as an offline snapshot that no longer loads in a release build.
 *
 * **The on-disk format.** The offline snapshot has to stay readable across builds, including files
 * written by the gson version - which is what `_items` is about.
 */
class JsonMappingTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun thread() = ZumpaThread("123", "a subject").apply {
        author = "someone"
        contentUrl = "https://zunpa.cz/x"
        time = 1_754_000_000_000L
        lastAuthor = "somebody else"
        isFavorite = true
        hasResponseForYou = true
        state = ZumpaThread.STATE_UPDATED
        offlineItems = listOf(
            ZumpaThreadItem("author one", "body one", 1L).apply {
                authorReal = "real one"
                urls = listOf("https://a.b/c.jpg")
                rating = "+3"
            },
            ZumpaThreadItem("author two", "body two", 2L),
        )
        items = 1
    }

    //region keys
    @Test
    fun `an offline thread serialises under exactly these keys`() {
        val encoded = json.parseToJsonElement(json.encodeToString(thread().toDto())).jsonObject

        assertEquals(
            setOf(
                "id", "subject", "author", "contentUrl", "time", "_items", "isFavorite", "state",
                "hasResponseForYou", "lastAuthor", "offlineItems",
            ),
            encoded.keys,
        )
    }

    @Test
    fun `an offline thread item serialises under exactly these keys`() {
        //every value is off its default on purpose - a default is not written out, and this test is
        //about the names the keys have rather than about which of them get emitted
        val item = ZumpaThreadItem("a", "b", 1L).apply {
            authorReal = "r"
            hasResponseForYou = true
            isOwnThread = true
            urls = listOf("u")
            rating = "-1"
        }
        val encoded = json.parseToJsonElement(json.encodeToString(item.toDto())).jsonObject

        assertEquals(
            setOf("author", "body", "time", "authorReal", "hasResponseForYou", "isOwnThread", "urls", "rating"),
            encoded.keys,
        )
    }

    @Test
    fun `a read state serialises under exactly these keys`() {
        val encoded = json.parseToJsonElement(json.encodeToString(ZumpaReadState("7", 3))).jsonObject

        assertEquals(setOf("threadId", "count"), encoded.keys)
    }
    //endregion

    //region offline snapshot round trip
    @Test
    fun `a snapshot survives a round trip through disk`() {
        val original = thread()
        val encoded = json.encodeToString(mapOf(original.id to original.toDto()))

        val restored = json.decodeFromString<Map<String, OfflineThreadDto>>(encoded)
            .getValue(original.id)
            .toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.subject, restored.subject)
        assertEquals(original.author, restored.author)
        assertEquals(original.time, restored.time)
        assertEquals(original.lastAuthor, restored.lastAuthor)
        assertEquals(2, restored.offlineItems?.size)
        assertEquals("author one", restored.offlineItems?.first()?.author)
        assertEquals("real one", restored.offlineItems?.first()?.authorReal)
        assertEquals(listOf("https://a.b/c.jpg"), restored.offlineItems?.first()?.urls)
    }

    @Test
    fun `reading a snapshot reproduces what the gson reader did with the derived fields`() {
        val restored = json.decodeFromString<Map<String, OfflineThreadDto>>(
            json.encodeToString(mapOf("123" to thread().toDto())),
        ).getValue("123").toDomain()

        //the count comes from the items and not from the file - two items means one reply
        assertEquals(1, restored.items)
        assertEquals(ZumpaThread.STATE_NONE, restored.state)
        //never restored by the old reader either
        assertEquals("", restored.contentUrl)
        assertEquals(false, restored.isFavorite)
        assertEquals(false, restored.hasResponseForYou)
    }

    @Test
    fun `a snapshot written by the gson version still loads`() {
        //`_items` is the private backing field gson reflected over, and the derived properties it
        //could not be stopped from emitting are here too - they have to be tolerated, not rejected
        val legacy = """
            {
              "42": {
                "id": "42",
                "subject": "legacy subject",
                "author": "legacy author",
                "contentUrl": "https://zunpa.cz/legacy",
                "time": 1700000000000,
                "_items": 5,
                "isFavorite": false,
                "state": 1,
                "hasResponseForYou": false,
                "lastAuthor": "last one",
                "offlineItems": [
                  { "author": "a1", "body": "b1", "time": 1, "authorReal": "r1", "urls": ["u1"] },
                  { "author": "a2", "body": "b2", "time": 2, "authorReal": "r2" }
                ]
              }
            }
        """.trimIndent()

        val restored = json.decodeFromString<Map<String, OfflineThreadDto>>(legacy)
            .getValue("42")
            .toDomain()

        assertEquals("legacy subject", restored.subject)
        assertEquals("legacy author", restored.author)
        assertEquals(1_700_000_000_000L, restored.time)
        assertEquals("last one", restored.lastAuthor)
        assertEquals(2, restored.offlineItems?.size)
        assertEquals(listOf("u1"), restored.offlineItems?.first()?.urls)
        assertNull(restored.offlineItems?.get(1)?.urls)
    }

    @Test
    fun `a thread with no items loads rather than throwing`() {
        val restored = json
            .decodeFromString<Map<String, OfflineThreadDto>>("""{"1":{"id":"1","subject":"s"}}""")
            .getValue("1")
            .toDomain()

        assertEquals(0, restored.items)
        assertEquals(emptyList<ZumpaThreadItem>(), restored.offlineItems)
    }
    //endregion

    //region prefetching
    @Test
    fun `only image urls are collected for prefetching`() {
        val urls = listOf(thread()).offlineImageUrls()

        assertEquals(setOf("https://a.b/c.jpg"), urls)
    }
    //endregion
}
