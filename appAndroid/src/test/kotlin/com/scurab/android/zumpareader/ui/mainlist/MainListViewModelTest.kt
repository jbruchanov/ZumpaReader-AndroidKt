package com.scurab.android.zumpareader.ui.mainlist

import app.cash.turbine.test
import com.scurab.android.zumpareader.arch.DeviceConfig
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.SelectedThreadStore
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MainListViewModelTest {

    private val isOffline = MutableStateFlow(false)
    private val isLoggedIn = MutableStateFlow(true)
    private val isLoggedInNotOffline = MutableStateFlow(true)
    private val filter = MutableStateFlow("0")
    private val loggedUserName = MutableStateFlow<String?>("me")

    private val settings = mockk<ZumpaSettingsRepository>(relaxed = true) {
        every { this@mockk.isOffline } returns this@MainListViewModelTest.isOffline
        every { this@mockk.isLoggedIn } returns this@MainListViewModelTest.isLoggedIn
        every { this@mockk.isLoggedInNotOffline } returns this@MainListViewModelTest.isLoggedInNotOffline
        every { this@mockk.filter } returns this@MainListViewModelTest.filter
        every { this@mockk.loggedUserName } returns this@MainListViewModelTest.loggedUserName
    }

    private val readStates = mockk<ZumpaReadStateRepository> {
        every { readCount(any()) } returns null
    }

    private val threads = mockk<ZumpaThreadRepository>(relaxed = true)
    private val selectedThread = SelectedThreadStore()
    private val eventBus = AppEventBus()

    private fun thread(
        id: String,
        author: String = "someone",
        items: Int = 5,
        responseForYou: Boolean = false,
    ) = ZumpaThread(id, "subject $id").apply {
        this.author = author
        this.items = items
        this.hasResponseForYou = responseForYou
    }

    private fun page(nextThreadId: String, vararg threads: ZumpaThread) = ZumpaMainPageResult(
        prevThreadId = null,
        nextThreadId = nextThreadId,
        items = LinkedHashMap(threads.associateBy { it.id })
    )

    private fun viewModel(isTablet: Boolean = false) = MainListViewModel(
        threads, settings, readStates, selectedThread, eventBus, DeviceConfig(isTablet)
    )

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the first load sorts newest first`() = runTest {
        coEvery { threads.loadMainPage(any(), any()) } returns
            page("9", thread("10"), thread("12"), thread("11"))

        val state = viewModel().uiState.value

        assertEquals(listOf("12", "11", "10"), state.rows.map { it.id })
    }

    @Test
    fun `paging appends the next page`() = runTest {
        coEvery { threads.loadMainPage(null, "0") } returns page("9", thread("11"), thread("10"))
        coEvery { threads.loadMainPage("9", "0") } returns page("7", thread("9"), thread("8"))
        val viewModel = viewModel()

        viewModel.onEndReached()

        assertEquals(listOf("11", "10", "9", "8"), viewModel.uiState.value.rows.map { it.id })
    }

    @Test
    fun `paging stops when the api reports no next thread`() = runTest {
        //the offline api always answers with an empty next id
        coEvery { threads.loadMainPage(any(), any()) } returns page("", thread("10"))
        val viewModel = viewModel()

        viewModel.onEndReached()

        coVerify(exactly = 1) { threads.loadMainPage(any(), any()) }
    }

    @Test
    fun `changing the filter starts the list over`() = runTest {
        coEvery { threads.loadMainPage(null, "0") } returns page("9", thread("11"), thread("10"))
        coEvery { threads.loadMainPage(null, "2") } returns page("5", thread("6"))
        val viewModel = viewModel()
        assertEquals(2, viewModel.uiState.value.rows.size)

        filter.value = "2"
        viewModel.onRefreshRequested()

        assertEquals(listOf("6"), viewModel.uiState.value.rows.map { it.id })
    }

    @Test
    fun `ignoring a thread takes it off the list`() = runTest {
        coEvery { threads.loadMainPage(any(), any()) } returns
            page("9", thread("11"), thread("10"))
        val viewModel = viewModel()

        viewModel.onIgnoreClicked("10")

        assertEquals(listOf("11"), viewModel.uiState.value.rows.map { it.id })
        coVerify { threads.toggleIgnore("10") }
    }

    @Test
    fun `a phone opens the thread while a tablet selects it`() = runTest {
        coEvery { threads.loadMainPage(any(), any()) } returns page("9", thread("10"))

        viewModel(isTablet = false).run {
            effects.test {
                onThreadClicked("10")
                assertEquals(MainListEffect.OpenThread("10"), awaitItem())
            }
        }

        viewModel(isTablet = true).run {
            effects.test {
                onThreadClicked("10")
                expectNoEvents()
            }
        }
        assertEquals("10", selectedThread.selected.value)
    }

    @Test
    fun `a response for you outranks everything else`() = runTest {
        coEvery { threads.loadMainPage(any(), any()) } returns
            page("9", thread("10", responseForYou = true))
        every { readStates.readCount("10") } returns 5

        assertEquals(ThreadState.ResponseForYou, viewModel().uiState.value.rows.single().state)
    }

    @Test
    fun `an unread thread is new and a fully read one is not`() = runTest {
        coEvery { threads.loadMainPage(any(), any()) } returns
            page("9", thread("10", items = 5), thread("11", items = 5))
        every { readStates.readCount("10") } returns null
        every { readStates.readCount("11") } returns 5

        val rows = viewModel().uiState.value.rows.associateBy { it.id }

        assertEquals(ThreadState.New, rows.getValue("10").state)
        assertEquals(ThreadState.None, rows.getValue("11").state)
    }

    @Test
    fun `a read thread of your own is marked as yours`() = runTest {
        coEvery { threads.loadMainPage(any(), any()) } returns
            page("9", thread("10", author = "me", items = 5))
        every { readStates.readCount("10") } returns 5

        assertEquals(ThreadState.Own, viewModel().uiState.value.rows.single().state)
    }

    @Test
    fun `new answers since the last read mark the thread updated`() = runTest {
        coEvery { threads.loadMainPage(any(), any()) } returns
            page("9", thread("10", items = 7))
        every { readStates.readCount("10") } returns 5

        assertEquals(ThreadState.Updated, viewModel().uiState.value.rows.single().state)
    }

    @Test
    fun `opening a thread clears its updated mark`() = runTest {
        coEvery { threads.loadMainPage(any(), any()) } returns page("9", thread("10", items = 7))
        every { readStates.readCount("10") } returns 5
        val viewModel = viewModel()
        assertEquals(ThreadState.Updated, viewModel.uiState.value.rows.single().state)

        viewModel.onThreadClicked("10")

        assertEquals(ThreadState.None, viewModel.uiState.value.rows.single().state)
    }

    @Test
    fun `the state bar levels still match the drawable`() {
        //ZumpaThread.STATE_NONE..STATE_RESPONSE_4U were 0..4 and index a LevelListDrawable
        assertEquals(0, ThreadState.None.ordinal)
        assertEquals(1, ThreadState.New.ordinal)
        assertEquals(2, ThreadState.Updated.ordinal)
        assertEquals(3, ThreadState.Own.ordinal)
        assertEquals(4, ThreadState.ResponseForYou.ordinal)
    }
}
