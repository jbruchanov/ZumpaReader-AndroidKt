package com.scurab.android.zumpareader.ui.post

import app.cash.turbine.test
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PostViewModelTest {

    private val threads = mockk<ZumpaThreadRepository>(relaxed = true) {
        every { thread("42") } returns ZumpaThread("42", "the original subject")
    }
    private val settings = mockk<ZumpaSettingsRepository>(relaxed = true) {
        every { nickName } returns "me"
    }

    private fun viewModel(args: PostArgs = PostArgs()) =
        PostViewModel(threads, settings, AppEventBus()).also { it.start(args) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a new thread starts with just the message tab`() {
        val state = viewModel().uiState.value

        assertEquals(listOf(PostTabUiState.Message), state.tabs)
        assertTrue(state.isSubjectEditable)
    }

    @Test
    fun `replying into a thread fixes the subject`() {
        assertFalse(viewModel(PostArgs(threadId = "42")).uiState.value.isSubjectEditable)
    }

    @Test
    fun `a new thread needs both a subject and a message`() {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.canSend)

        viewModel.onSubjectChanged("hello")
        assertFalse(viewModel.uiState.value.canSend)

        viewModel.onMessageChanged("world")
        assertTrue(viewModel.uiState.value.canSend)
    }

    @Test
    fun `a reply needs only a message`() {
        val viewModel = viewModel(PostArgs(threadId = "42"))

        viewModel.onMessageChanged("world")

        assertTrue(viewModel.uiState.value.canSend)
    }

    @Test
    fun `sending without a subject is refused`() = runTest {
        val viewModel = viewModel()
        viewModel.onMessageChanged("body")

        viewModel.effects.test {
            viewModel.onSendClicked()
            assertEquals(ShowToast(resId = R.string.err_empty_subject), awaitItem())
        }
        coVerify(exactly = 0) { threads.sendThread(any()) }
    }

    @Test
    fun `sending without a message is refused`() = runTest {
        val viewModel = viewModel()
        viewModel.onSubjectChanged("subj")

        viewModel.effects.test {
            viewModel.onSendClicked()
            assertEquals(ShowToast(resId = R.string.err_empty_msg), awaitItem())
        }
    }

    @Test
    fun `a new thread is posted as a thread`() = runTest {
        val viewModel = viewModel()
        viewModel.onSubjectChanged("subj")
        viewModel.onMessageChanged("body")
        val body = slot<ZumpaThreadBody>()

        viewModel.onSendClicked()

        coVerify { threads.sendThread(capture(body)) }
        assertEquals("subj", body.captured.subject)
        assertEquals("body", body.captured.body)
        assertEquals("me", body.captured.author)
    }

    @Test
    fun `a reply keeps the thread's own subject rather than the argument`() = runTest {
        val viewModel = viewModel(PostArgs(subject = "stale copy", threadId = "42"))
        viewModel.onMessageChanged("body")
        val body = slot<ZumpaThreadBody>()

        viewModel.onSendClicked()

        coVerify { threads.sendResponse("42", capture(body)) }
        assertEquals("the original subject", body.captured.subject)
    }

    @Test
    fun `an uploaded image link lands in the message and brings the tab forward`() {
        val viewModel = viewModel()
        viewModel.onMessageChanged("look at this")

        viewModel.onLinkShared("http://x/pic.jpg")

        assertEquals("look at this\n<http://x/pic.jpg>\n", viewModel.uiState.value.message)
        assertEquals(POST_MESSAGE_TAB, viewModel.uiState.value.selectedTabTag)
    }

    @Test
    fun `a link into an empty message gets no leading newline`() {
        val viewModel = viewModel()

        viewModel.onLinkShared("http://x/pic.jpg")

        assertEquals("<http://x/pic.jpg>\n", viewModel.uiState.value.message)
    }

    @Test
    fun `the launch picker opens exactly once`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onPicker(PostPicker.Camera)
            assertEquals(PostEffect.RequestCameraImage, awaitItem())
            //a rotation replays the argument, it must not reopen the camera
            viewModel.onPicker(PostPicker.Camera)
            expectNoEvents()
        }
    }

    @Test
    fun `no launch picker opens nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onPicker(null)
            expectNoEvents()
        }
    }

    @Test
    fun `start is idempotent so a rotation does not rebuild the tabs`() {
        val viewModel = viewModel(PostArgs(subject = "one"))

        viewModel.onSubjectChanged("edited by the user")
        viewModel.start(PostArgs(subject = "one"))

        assertEquals("edited by the user", viewModel.uiState.value.subject)
    }
}
