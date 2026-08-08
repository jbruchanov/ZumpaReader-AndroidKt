package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThread
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ZumpaThreadRepositoryTest {

    private fun page(vararg ids: String) = ZumpaMainPageResult(
        prevThreadId = null,
        nextThreadId = "",
        items = LinkedHashMap(ids.associateWith { ZumpaThread(it, "subject $it") })
    )

    /**
     * The trap this whole design exists for: the unqualified ZumpaAPI binding is a koin factory
     * because offline is a runtime setting. A repository that took an instance would keep serving
     * the online api after the user went offline.
     */
    @Test
    fun `resolves the api per call so the offline switch takes effect`() = runTest {
        val online = mockk<ZumpaAPI>()
        val offline = mockk<ZumpaAPI>()
        coEvery { online.getMainPage(any<String>()) } returns page("1")
        coEvery { offline.getMainPage(any<String>()) } returns page("2")

        var isOffline = false
        val repository = ZumpaThreadRepositoryImpl(api = { if (isOffline) offline else online })

        repository.loadMainPage(fromThread = null, filter = "0")
        isOffline = true
        repository.loadMainPage(fromThread = null, filter = "0")

        coVerify(exactly = 1) { online.getMainPage("0") }
        coVerify(exactly = 1) { offline.getMainPage("0") }
    }

    @Test
    fun `a load publishes the accumulated threads`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page("10", "11")
        coEvery { api.getMainPage(any<String>(), any<String>()) } returns page("12")
        val repository = ZumpaThreadRepositoryImpl(api = { api })

        assertEquals(emptyMap<String, ZumpaThread>(), repository.threads.value)

        repository.loadMainPage(fromThread = null, filter = "0")
        assertEquals(setOf("10", "11"), repository.threads.value.keys)

        repository.loadMainPage(fromThread = "11", filter = "0")
        assertEquals(setOf("10", "11", "12"), repository.threads.value.keys)
    }

    @Test
    fun `removing a thread publishes the change`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page("10", "11")
        val repository = ZumpaThreadRepositoryImpl(api = { api })
        repository.loadMainPage(fromThread = null, filter = "0")

        repository.remove("10")

        assertEquals(setOf("11"), repository.threads.value.keys)
        assertNull(repository.thread("10"))
    }

    @Test
    fun `lastThread is the highest key - what the tablet opens on first load`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page("10", "12", "11")
        val repository = ZumpaThreadRepositoryImpl(api = { api })
        repository.loadMainPage(fromThread = null, filter = "0")

        assertEquals("12", repository.lastThread()?.id)
    }

    @Test
    fun `replaceAll drops what was there - the offline download result wins`() = runTest {
        val api = mockk<ZumpaAPI>()
        coEvery { api.getMainPage(any<String>()) } returns page("10", "11")
        val repository = ZumpaThreadRepositoryImpl(api = { api })
        repository.loadMainPage(fromThread = null, filter = "0")

        repository.replaceAll(mapOf("99" to ZumpaThread("99", "offline")))

        assertEquals(setOf("99"), repository.threads.value.keys)
    }
}
