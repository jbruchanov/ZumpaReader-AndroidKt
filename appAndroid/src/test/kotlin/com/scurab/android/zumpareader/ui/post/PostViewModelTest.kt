package com.scurab.android.zumpareader.ui.post

import app.cash.turbine.test
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.ShowSnackbar
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import com.scurab.android.zumpareader.repository.InMemorySentDraftRepository
import com.scurab.android.zumpareader.repository.SentDraft
import org.junit.jupiter.api.Test

class PostViewModelTest {

    private val threads = mockk<ZumpaThreadRepository>(relaxed = true) {
        every { thread("42") } returns ZumpaThread("42", "the original subject")
    }
    private val settings = mockk<ZumpaSettingsRepository>(relaxed = true) {
        every { nickName } returns "me"
    }

    //the real one - it is a MutableStateFlow behind an interface, and a mock would only be a
    //slower way of writing the same thing
    private val sentDrafts = InMemorySentDraftRepository()

    private fun viewModel(args: PostArgs = PostArgs()) =
        PostViewModel(threads, settings, AppEventBus(), sentDrafts).also { it.start(args) }

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
            assertEquals(ShowSnackbar(resId = R.string.err_empty_subject), awaitItem())
        }
        coVerify(exactly = 0) { threads.sendThread(any()) }
    }

    @Test
    fun `sending without a message is refused`() = runTest {
        val viewModel = viewModel()
        viewModel.onSubjectChanged("subj")

        viewModel.effects.test {
            viewModel.onSendClicked()
            assertEquals(ShowSnackbar(resId = R.string.err_empty_msg), awaitItem())
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

    /**
     * The forum only makes a link clickable inside `<>`, so urls in the outgoing message are
     * wrapped for it. The draft the writer saw stays as they typed it - see the next test.
     */
    @Test
    fun `urls in the outgoing message are wrapped in angle brackets`() = runTest {
        val viewModel = viewModel()
        viewModel.onSubjectChanged("subj")
        viewModel.onMessageChanged("see https://a.b/c and www.d.e")
        val body = slot<ZumpaThreadBody>()

        viewModel.onSendClicked()

        coVerify { threads.sendThread(capture(body)) }
        //`www.` needs a scheme too - see ZumpaSimpleParser.replaceLinksByZumpaLinks
        assertEquals("see <https://a.b/c> and <https://www.d.e>", body.captured.body)
    }

    /** Restoring later has to give back what the writer typed, not the link-wrapped rewrite. */
    @Test
    fun `the saved draft is the writer's text - not the wrapped version`() {
        val viewModel = viewModel()
        viewModel.onSubjectChanged("subj")
        viewModel.onMessageChanged("see https://a.b/c")

        viewModel.onSendClicked()

        assertEquals(SentDraft("see https://a.b/c", "subj"), sentDrafts.draft.value)
    }

    @Test
    fun `every picture adds a tab of its own`() {
        val vm = viewModel()
        val picks =
            listOf(pick(fromCamera = false), pick(fromCamera = false), pick(fromCamera = true))

        //as the screen hands them over: the whole list, growing by one each time
        vm.applyPicks(picks.take(1))
        vm.applyPicks(picks.take(2))
        vm.applyPicks(picks)

        //the message tab and one per picture, none of them replacing an earlier one
        val tabs = vm.uiState.value.tabs
        assertEquals(4, tabs.size)
        assertEquals(3, tabs.filterIsInstance<PostTabUiState.Image>().size)
        //distinct tags, or two tabs would share one image ViewModel
        assertEquals(tabs.size, tabs.map { it.tag }.toSet().size)
    }

    @Test
    fun `the same picks rebuild the same tabs`() {
        val vm = viewModel()
        val picks = listOf(pick(fromCamera = false), pick(fromCamera = true))

        vm.applyPicks(picks)
        val first = vm.uiState.value.tabs.map { it.tag }
        //what a recreation does: the screen still has the list and hands it over again
        vm.applyPicks(picks)

        //same tags, so the per tab upload ViewModels are the same ones and nothing restarts
        assertEquals(first, vm.uiState.value.tabs.map { it.tag })
        assertEquals(3, vm.uiState.value.tabs.size)
    }

    @Test
    fun `every uploaded link is appended to the message`() {
        val vm = viewModel()

        vm.onLinkShared("http://x.com/1.jpg")
        vm.onLinkShared("http://x.com/2.jpg")

        val message = vm.uiState.value.message
        assertTrue(message.contains("<http://x.com/1.jpg>"), message)
        assertTrue(message.contains("<http://x.com/2.jpg>"), message)
    }

    @Test
    fun `a picked picture opens on its own tab`() {
        val vm = viewModel()

        vm.applyPicks(listOf(pick(fromCamera = true)))

        val tabs = vm.uiState.value.tabs
        assertEquals(2, tabs.size)
        //the tab the picture went on, not the message tab it was taken from
        assertEquals(tabs.last().tag, vm.uiState.value.selectedTabTag)
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

    //region the last sent message
    /**
     * Saved as send is pressed, not when the forum answers. The forum sometimes accepts a post,
     * reports success and does nothing with it - a draft kept only on a failure would be missing
     * for exactly the posts this exists for.
     */
    @Test
    fun `a new thread is saved with its subject the moment send is pressed`() {
        val viewModel = viewModel()
        viewModel.onSubjectChanged("a subject")
        viewModel.onMessageChanged("a message")

        viewModel.onSendClicked()

        assertEquals(SentDraft("a message", "a subject"), sentDrafts.draft.value)
    }

    /** A reply's subject belongs to the thread, so there is none of the writer's to keep. */
    @Test
    fun `a reply is saved without a subject`() {
        val viewModel = viewModel(PostArgs(threadId = "42"))
        viewModel.onMessageChanged("an answer")

        viewModel.onSendClicked()

        assertEquals(SentDraft("an answer", null), sentDrafts.draft.value)
    }

    @Test
    fun `nothing is saved by a send the screen refuses`() {
        val viewModel = viewModel()
        viewModel.onSubjectChanged("a subject")

        //no message, so the screen turns it down before it reaches the forum
        viewModel.onSendClicked()

        assertNull(sentDrafts.draft.value)
    }

    @Test
    fun `an empty field is filled without asking`() {
        sentDrafts.save("what was sent", "the old subject")
        val viewModel = viewModel()

        viewModel.onRestoreDraftClicked()

        assertEquals("what was sent", viewModel.uiState.value.message)
        assertEquals("the old subject", viewModel.uiState.value.subject)
        assertNull(viewModel.uiState.value.restorePrompt)
    }

    @Test
    fun `a field with something in it asks first`() {
        sentDrafts.save("what was sent", null)
        val viewModel = viewModel()
        viewModel.onMessageChanged("half a thought")

        viewModel.onRestoreDraftClicked()

        assertEquals(SentDraft("what was sent", null), viewModel.uiState.value.restorePrompt)
        assertEquals("half a thought", viewModel.uiState.value.message)
    }

    @Test
    fun `append adds the saved message to what is already written`() {
        sentDrafts.save("what was sent", null)
        val viewModel = viewModel()
        viewModel.onMessageChanged("half a thought")
        viewModel.onRestoreDraftClicked()

        viewModel.onRestoreDraftAppended()

        assertEquals("half a thought\nwhat was sent", viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.restorePrompt)
    }

    @Test
    fun `overwrite replaces it`() {
        sentDrafts.save("what was sent", null)
        val viewModel = viewModel()
        viewModel.onMessageChanged("half a thought")
        viewModel.onRestoreDraftClicked()

        viewModel.onRestoreDraftOverwritten()

        assertEquals("what was sent", viewModel.uiState.value.message)
    }

    @Test
    fun `cancel leaves what was written alone`() {
        sentDrafts.save("what was sent", null)
        val viewModel = viewModel()
        viewModel.onMessageChanged("half a thought")
        viewModel.onRestoreDraftClicked()

        viewModel.onRestoreDraftDismissed()

        assertEquals("half a thought", viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.restorePrompt)
    }

    /** The subject of a thread being answered is not the writer's, whatever was saved. */
    @Test
    fun `a saved subject does not reach a reply`() {
        sentDrafts.save("what was sent", "the old subject")
        val viewModel = viewModel(PostArgs(threadId = "42", subject = "the original subject"))

        viewModel.onRestoreDraftClicked()

        assertEquals("what was sent", viewModel.uiState.value.message)
        assertEquals("the original subject", viewModel.uiState.value.subject)
    }

    /** Saved from a reply, so there is no subject to offer and the typed one stands. */
    @Test
    fun `restoring a reply into a new thread fills only the message`() {
        sentDrafts.save("what was sent", null)
        val viewModel = viewModel()
        viewModel.onSubjectChanged("what I am writing now")

        viewModel.onRestoreDraftClicked()

        assertEquals("what was sent", viewModel.uiState.value.message)
        assertEquals("what I am writing now", viewModel.uiState.value.subject)
    }

    @Test
    fun `there is nothing to restore before anything has been sent`() {
        val viewModel = viewModel()
        viewModel.onMessageChanged("half a thought")

        viewModel.onRestoreDraftClicked()

        assertNull(viewModel.uiState.value.sentDraft)
        assertNull(viewModel.uiState.value.restorePrompt)
        assertEquals("half a thought", viewModel.uiState.value.message)
    }
    //endregion

    /**
     * mockk rather than Uri.parse: android.net.Uri is a stub on the jvm, and nothing here does more
     * with it than hold on to it.
     */
    private fun pick(fromCamera: Boolean) =
        PickedImage(mockk<android.net.Uri>(relaxed = true), fromCamera)
}
