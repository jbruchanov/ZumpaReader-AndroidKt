package com.scurab.android.zumpareader.usecase

import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.repository.ImagePrefetcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The download used to be one call to a web service that no longer exists. What replaces it is a
 * walk over the pages the app already reads, so these cover the walk: how many list pages it asks
 * for, when it stops early, and that every thread gets its bodies fetched.
 */
class OfflineDownloadUseCaseTest {

    private val images = mockk<ImagePrefetcher>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun page(next: String, vararg ids: String) = ZumpaMainPageResult(
        prevThreadId = null,
        nextThreadId = next,
        items = LinkedHashMap(ids.associateWith { ZumpaThread(it, "subject $it") }),
    )

    private fun body(id: String) = ZumpaThreadResult(
        listOf(ZumpaThreadItem("author $id", "body $id", 1L)),
    )

    private fun useCase(api: ZumpaAPI) = OfflineDownloadUseCase(api, images, json)

    @Test
    fun `walks as many list pages as it was asked for`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page(next = "1", "1")
        coEvery { api.getMainPage("1", any<String>()) } returns page(next = "2", "2")
        coEvery { api.getMainPage("2", any<String>()) } returns page(next = "3", "3")
        coEvery { api.getThreadPage(any(), any()) } answers { body(firstArg()) }

        val done = useCase(api).run(pages = 3, downloadImages = false, outJsonFile = null)
            .toList()
            .filterIsInstance<OfflineProgress.Done>()
            .single()

        assertEquals(setOf("1", "2", "3"), done.data.keys)
        coVerify(exactly = 1) { api.getMainPage(any<String>()) }
        coVerify(exactly = 1) { api.getMainPage("1", any<String>()) }
        coVerify(exactly = 1) { api.getMainPage("2", any<String>()) }
    }

    /** An empty next id is the end of the forum - asking for ten pages of a two page list is fine. */
    @Test
    fun `stops early when the list runs out`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page(next = "1", "1")
        coEvery { api.getMainPage("1", any<String>()) } returns page(next = "", "2")
        coEvery { api.getThreadPage(any(), any()) } answers { body(firstArg()) }

        val done = useCase(api).run(pages = 10, downloadImages = false, outJsonFile = null)
            .toList()
            .filterIsInstance<OfflineProgress.Done>()
            .single()

        assertEquals(setOf("1", "2"), done.data.keys)
        coVerify(exactly = 1) { api.getMainPage("1", any<String>()) }
    }

    /**
     * The point of the whole thing: the list page carries no message bodies, so a snapshot is only
     * usable if every thread is fetched as well. That is what the web service used to hand over.
     */
    @Test
    fun `fetches the bodies of every listed thread`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page(next = "", "7", "8")
        coEvery { api.getThreadPage(any(), any()) } answers { body(firstArg()) }

        val done = useCase(api).run(pages = 1, downloadImages = false, outJsonFile = null)
            .toList()
            .filterIsInstance<OfflineProgress.Done>()
            .single()

        assertEquals(listOf("body 7"), done.data.getValue("7").offlineItems?.map { it.body })
        assertEquals(listOf("body 8"), done.data.getValue("8").offlineItems?.map { it.body })
        coVerify(exactly = 1) { api.getThreadPage("7", "7") }
        coVerify(exactly = 1) { api.getThreadPage("8", "8") }
    }

    @Test
    fun `reports the thread count as it goes rather than in one lump`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page(next = "", "1", "2", "3")
        coEvery { api.getThreadPage(any(), any()) } answers { body(firstArg()) }

        val progress = useCase(api).run(pages = 1, downloadImages = false, outJsonFile = null)
            .toList()
            .filterIsInstance<OfflineProgress.Threads>()

        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), progress.map { it.done to it.total })
    }

    @Test
    fun `prefetches only image urls and only when asked`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page(next = "", "1")
        coEvery { api.getThreadPage(any(), any()) } returns ZumpaThreadResult(
            listOf(
                ZumpaThreadItem("a", "b", 1L).apply {
                    urls = listOf("https://a.b/c.jpg", "https://d.e/not-an-image")
                },
            ),
        )

        useCase(api).run(pages = 1, downloadImages = true, outJsonFile = null).toList()

        coVerify(exactly = 1) { images.prefetch("https://a.b/c.jpg") }
        coVerify(exactly = 0) { images.prefetch("https://d.e/not-an-image") }
    }

    /**
     * The download is N+1 requests now, so a single thread page that will not load is the normal
     * failure - it must cost that one thread and not the whole snapshot.
     */
    @Test
    fun `a thread page that fails is skipped rather than losing the download`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page(next = "", "1", "2", "3")
        coEvery { api.getThreadPage(any(), any()) } answers { body(firstArg()) }
        coEvery { api.getThreadPage("2", "2") } throws IllegalStateException("boom")

        val done = useCase(api).run(pages = 1, downloadImages = false, outJsonFile = null)
            .toList()
            .filterIsInstance<OfflineProgress.Done>()
            .single()

        assertEquals(setOf("1", "3"), done.data.keys)
    }

    /** Nor may a snapshot that cannot be written throw away what was just downloaded. */
    @Test
    fun `an unwritable snapshot still reports the downloaded threads`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page(next = "", "1")
        coEvery { api.getThreadPage(any(), any()) } answers { body(firstArg()) }

        val done = useCase(api)
            .run(pages = 1, downloadImages = false, outJsonFile = "/no/such/directory/out.json")
            .toList()
            .filterIsInstance<OfflineProgress.Done>()
            .single()

        assertEquals(setOf("1"), done.data.keys)
        assertFalse(done.snapshotWritten)
    }

    @Test
    fun `a written snapshot says so`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page(next = "", "1")
        coEvery { api.getThreadPage(any(), any()) } answers { body(firstArg()) }
        val file = kotlin.io.path.createTempFile("snapshot", ".json")

        val done = useCase(api)
            .run(pages = 1, downloadImages = false, outJsonFile = file.toString())
            .toList()
            .filterIsInstance<OfflineProgress.Done>()
            .single()

        assertTrue(done.snapshotWritten)
        assertTrue(file.toFile().readText().contains("\"1\""))
    }
}
