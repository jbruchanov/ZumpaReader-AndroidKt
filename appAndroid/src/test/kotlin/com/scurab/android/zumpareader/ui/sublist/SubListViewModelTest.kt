package com.scurab.android.zumpareader.ui.sublist

import app.cash.turbine.test
import com.scurab.android.zumpareader.arch.CopyToClipboard
import com.scurab.android.zumpareader.arch.WindowLayout
import com.scurab.android.zumpareader.model.Survey
import com.scurab.android.zumpareader.model.SurveyItem
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.repository.AppEvent
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.InMemorySentDraftRepository
import com.scurab.android.zumpareader.repository.SelectedThreadStore
import com.scurab.android.zumpareader.repository.SentDraft
import com.scurab.android.zumpareader.repository.ZumpaReadStateRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.jupiter.api.Test

class SubListViewModelTest {

    private val isLoggedInNotOffline = MutableStateFlow(true)
    private val loadImages = MutableStateFlow(true)

    private val settings = mockk<ZumpaSettingsRepository>(relaxed = true) {
        every { this@mockk.isLoggedInNotOffline } returns this@SubListViewModelTest.isLoggedInNotOffline
        every { this@mockk.loadImages } returns this@SubListViewModelTest.loadImages
        every { nickName } returns "me"
    }
    private val readStates = mockk<ZumpaReadStateRepository>(relaxed = true)
    private val threads = mockk<ZumpaThreadRepository>(relaxed = true) {
        every { thread(any()) } returns ZumpaThread("1", "the subject")
    }
    private val selectedThread = SelectedThreadStore()

    private fun item(
        author: String = "someone",
        body: String = "hello",
        urls: List<String>? = null,
        survey: Survey? = null,
    ) = ZumpaThreadItem(author, body, 0L).apply {
        this.authorReal = author
        this.urls = urls
        this.survey = survey
    }

    private val sentDrafts = InMemorySentDraftRepository()

    //held rather than built inline: the tests below post from "somewhere else" by emitting on it
    private val eventBus = AppEventBus()

    private fun viewModel(isTwoPane: Boolean = false) = SubListViewModel(
        threads, settings, readStates, selectedThread, eventBus, WindowLayout(isTwoPane),
        sentDrafts,
    ).also { it.start("1") }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    //region row flattening
    @Test
    fun `a message with no urls is a single row`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())

        val rows = viewModel().uiState.value.rows

        assertEquals(1, rows.size)
        assertTrue(rows.single() is SubListRowUiState.Message)
    }

    @Test
    fun `images come before plain links under their message`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(
            item(urls = listOf("http://x/page.html", "http://x/pic.jpg", "http://x/other"))
        )

        val rows = viewModel().uiState.value.rows

        assertEquals(
            listOf(
                SubListRowUiState.Message::class,
                SubListRowUiState.Image::class,
                SubListRowUiState.Link::class,
                SubListRowUiState.Link::class,
            ),
            rows.map { it::class }
        )
    }

    @Test
    fun `an image becomes a plain link when image loading is off`() = runTest {
        loadImages.value = false
        coEvery { threads.loadThread("1") } returns listOf(item(urls = listOf("http://x/pic.jpg")))

        val rows = viewModel().uiState.value.rows

        assertTrue(rows.last() is SubListRowUiState.Link)
    }

    @Test
    fun `only the opening post carries a survey`() = runTest {
        val survey = Survey("s1", "which one?", 10, listOf(SurveyItem(1, "s1", "a", 50, false)))
        coEvery { threads.loadThread("1") } returns listOf(
            item(survey = survey),
            item(survey = survey),
        )

        val rows = viewModel().uiState.value.rows

        assertEquals(1, rows.count { it is SubListRowUiState.Survey })
        assertEquals(0, rows.filterIsInstance<SubListRowUiState.Survey>().single().itemIndex)
    }

    @Test
    fun `loading a thread records everything but the opening post as read`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item(), item(), item())

        viewModel()

        coVerify { readStates.markRead("1", 2) }
    }
    //endregion

    //region the reply draft
    @Test
    fun `replying inserts the author header`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()

        viewModel.onReplyClicked("bob")

        assertEquals("@bob: \n", viewModel.uiState.value.draft.text)
        assertTrue(viewModel.uiState.value.isPostPanelVisible)
    }

    @Test
    fun `replying to the same author again takes the header back out`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()

        viewModel.onReplyClicked("bob")
        viewModel.onReplyClicked("bob")

        assertEquals("", viewModel.uiState.value.draft.text)
    }

    @Test
    fun `headers stack in insertion order ahead of the typed body`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()

        viewModel.onReplyClicked("bob")
        viewModel.onDraftChanged("@bob: \nmy answer")
        viewModel.onReplyClicked("ann")

        assertEquals("@bob: \n@ann: \nmy answer", viewModel.uiState.value.draft.text)
    }

    @Test
    fun `removing one header keeps the typed body`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()

        viewModel.onReplyClicked("bob")
        viewModel.onDraftChanged("@bob: \nmy answer")
        viewModel.onReplyClicked("bob")

        assertEquals("my answer", viewModel.uiState.value.draft.text)
    }

    @Test
    fun `deleting a header by hand stops it being a header`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()
        viewModel.onReplyClicked("bob")

        viewModel.onDraftChanged("just my own text")

        assertEquals(emptyList<String>(), viewModel.uiState.value.draft.headers)
        assertEquals("just my own text", viewModel.uiState.value.draft.text)
    }

    @Test
    fun `quoting appends the whole message at the end`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()

        viewModel.onQuoteClicked("bob", "the original")

        assertEquals("bob: the original\n----\n", viewModel.uiState.value.draft.text)
    }

    @Test
    fun `a second quote is separated from the first`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()

        viewModel.onQuoteClicked("bob", "one")
        viewModel.onQuoteClicked("ann", "two")

        assertEquals("bob: one\n----\n\nann: two\n----\n", viewModel.uiState.value.draft.text)
    }

    @Test
    fun `nothing can be drafted without a session`() = runTest {
        isLoggedInNotOffline.value = false
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()

        viewModel.onReplyClicked("bob")
        viewModel.onQuoteClicked("bob", "x")

        assertEquals("", viewModel.uiState.value.draft.text)
    }
    //endregion

    //region sending
    @Test
    fun `sending posts the draft and clears it`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()
        viewModel.onDraftChanged("my answer")
        val body = slot<ZumpaThreadBody>()

        viewModel.onSendClicked()

        coVerify { threads.sendResponse("1", capture(body)) }
        assertEquals("my answer", body.captured.body)
        assertEquals("the subject", body.captured.subject)
        assertEquals("", viewModel.uiState.value.draft.text)
        assertFalse(viewModel.uiState.value.isPostPanelVisible)
    }

    @Test
    fun `an empty draft is not sent`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()

        viewModel.onDraftChanged("   ")
        viewModel.onSendClicked()

        coVerify(exactly = 0) { threads.sendResponse(any(), any()) }
    }

    @Test
    fun `sending scrolls to the newest answer`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()
        viewModel.onDraftChanged("my answer")

        viewModel.effects.test {
            viewModel.onSendClicked()
            //HideKeyboard first then the scroll
            awaitItem()
            //the last row of what the reload published, not of what was on screen before it
            assertEquals(SubListEffect.ScrollToBottom(viewModel.uiState.value.rows.lastIndex), awaitItem())
        }
    }
    //endregion

    @Test
    fun `back closes the reply panel before it leaves the screen`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()
        viewModel.showPostPanel()

        assertTrue(viewModel.onBackPressed())
        assertFalse(viewModel.uiState.value.isPostPanelVisible)
        assertFalse(viewModel.onBackPressed())
    }

    @Test
    fun `holding a link or a picture copies its address`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())

        viewModel().run {
            effects.test {
                onLinkLongPressed("http://x.com/a.jpg")
                assertEquals(CopyToClipboard("http://x.com/a.jpg"), awaitItem())
            }
        }
    }

    @Test
    fun `a thread link opens a new screen with one pane and swaps the pane with two`() = runTest {
        coEvery { threads.loadThread(any()) } returns listOf(item())

        viewModel(isTwoPane = false).run {
            effects.test {
                onThreadLinkClicked("77")
                assertEquals(SubListEffect.OpenThread("77"), awaitItem())
            }
        }

        viewModel(isTwoPane = true).onThreadLinkClicked("88")
        assertEquals("88", selectedThread.selected.value)
    }

    @Test
    fun `a selection elsewhere is ignored when this screen is the only pane`() = runTest {
        coEvery { threads.loadThread(any()) } returns listOf(item())
        val viewModel = viewModel(isTwoPane = false)

        selectedThread.select("42")

        //with one pane the thread came from the back stack, and the selection is only a note of
        //where the user was for the next rotation
        assertEquals("1", viewModel.uiState.value.threadId)
    }

    @Test
    fun `selecting a thread elsewhere loads it here`() = runTest {
        coEvery { threads.loadThread(any()) } returns listOf(item())
        val viewModel = viewModel(isTwoPane = true)

        selectedThread.select("42")

        assertEquals("42", viewModel.uiState.value.threadId)
        coVerify { threads.loadThread("42") }
    }

    //region the last sent message
    /**
     * Saved as send is pressed, not when the forum answers - the forum sometimes accepts a reply,
     * reports success and does nothing with it, which is the whole reason this exists.
     */
    @Test
    fun `a sent reply is saved without a subject of its own`() {
        val viewModel = viewModel()
        viewModel.onDraftChanged("an answer")

        viewModel.onSendClicked()

        assertEquals(SentDraft("an answer", null), sentDrafts.draft.value)
    }

    @Test
    fun `an empty field is filled without asking`() {
        sentDrafts.save("what was sent", null)
        val viewModel = viewModel()

        viewModel.onRestoreDraftClicked()

        assertEquals("what was sent", viewModel.uiState.value.draft.text)
        assertNull(viewModel.uiState.value.restorePrompt)
    }

    @Test
    fun `a field with something in it asks first`() {
        sentDrafts.save("what was sent", null)
        val viewModel = viewModel()
        viewModel.onDraftChanged("half a thought")

        viewModel.onRestoreDraftClicked()

        assertEquals(SentDraft("what was sent", null), viewModel.uiState.value.restorePrompt)
        assertEquals("half a thought", viewModel.uiState.value.draft.text)
    }

    @Test
    fun `overwrite replaces what was written`() {
        sentDrafts.save("what was sent", null)
        val viewModel = viewModel()
        viewModel.onDraftChanged("half a thought")
        viewModel.onRestoreDraftClicked()

        viewModel.onRestoreDraftOverwritten()

        assertEquals("what was sent", viewModel.uiState.value.draft.text)
        assertNull(viewModel.uiState.value.restorePrompt)
    }

    /**
     * The `@author: ` prefixes are the reply's addressing, not part of what was written, so a
     * restore lands in the body and leaves them where they are.
     */
    @Test
    fun `restoring keeps the reply headers`() {
        sentDrafts.save("what was sent", null)
        val viewModel = viewModel()
        viewModel.onReplyClicked("someone")

        viewModel.onRestoreDraftClicked()

        //the header carries its own newline - see REPLY_HEADER
        assertEquals(listOf("@someone: \n"), viewModel.uiState.value.draft.headers)
        assertEquals("what was sent", viewModel.uiState.value.draft.body)
    }
    /**
     * Load-bearing for the caret: the field mirrors `draft.text` and puts the caret at the end
     * whenever it differs from what is in the box, so that a restored message is typed into from
     * the end. If reparsing what was typed did not give it back verbatim, that would fire on every
     * keystroke instead and the caret would jump to the end mid-word.
     */
    @Test
    fun `what is typed into the reply comes back out of the draft verbatim`() {
        val viewModel = viewModel()
        viewModel.onReplyClicked("someone")

        val typed = viewModel.uiState.value.draft.text + "an answer"
        viewModel.onDraftChanged(typed)

        assertEquals(typed, viewModel.uiState.value.draft.text)

        //and again with the header half deleted, which is the case reparse exists for
        val edited = typed.removePrefix("@")
        viewModel.onDraftChanged(edited)

        assertEquals(edited, viewModel.uiState.value.draft.text)
    }
    //endregion

    //region a post made from the full-screen writer
    /**
     * The panel's photo and camera buttons open the full-screen writer, and the post goes through
     * over there. What is still sitting in the panel has been said by then, so it goes the same way
     * pressing send here would have sent it.
     */
    @Test
    fun `a post into this thread from elsewhere clears and hides the reply panel`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()
        viewModel.onPostPanelRequested()
        viewModel.onDraftChanged("what I was writing")

        eventBus.emit(AppEvent.ContentPosted(threadId = "1"))

        assertEquals("", viewModel.uiState.value.draft.text)
        assertFalse(viewModel.uiState.value.isPostPanelVisible)
    }

    /** Somebody else's post is not a reason to throw away what is being written here. */
    @Test
    fun `a new thread posted from the list leaves this draft alone`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()
        viewModel.onPostPanelRequested()
        viewModel.onDraftChanged("what I was writing")

        eventBus.emit(AppEvent.ContentPosted(threadId = null))

        assertEquals("what I was writing", viewModel.uiState.value.draft.text)
        assertTrue(viewModel.uiState.value.isPostPanelVisible)
    }

    @Test
    fun `a post into a different thread leaves this draft alone`() = runTest {
        coEvery { threads.loadThread("1") } returns listOf(item())
        val viewModel = viewModel()
        viewModel.onPostPanelRequested()
        viewModel.onDraftChanged("what I was writing")

        eventBus.emit(AppEvent.ContentPosted(threadId = "99"))

        assertEquals("what I was writing", viewModel.uiState.value.draft.text)
    }
    //endregion
}
