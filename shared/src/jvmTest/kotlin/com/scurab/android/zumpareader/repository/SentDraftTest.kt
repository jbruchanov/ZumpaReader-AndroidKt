package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.util.InMemoryKeyValueStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The subject is the half worth pinning: what is saved and what is being written need not be the
 * same kind of post, and getting it wrong either drops a subject the writer wanted or replaces one
 * they typed.
 */
class SentDraftTest {

    private val fromNewThread = SentDraft(message = "the body", subject = "the subject")
    private val fromReply = SentDraft(message = "the body", subject = null)

    //region into a reply - the thread owns the subject
    @Test
    fun `a saved subject is dropped when the answer goes into an existing thread`() {
        val restored = fromNewThread.restoredInto(Draft("", subject = null), RestoreMode.Fill)

        assertEquals("the body", restored.message)
        assertNull(restored.subject)
    }
    //endregion

    //region into a new thread
    @Test
    fun `a draft saved as a reply fills the message and leaves the subject as typed`() {
        val current = Draft("", subject = "typed")

        val restored = fromReply.restoredInto(current, RestoreMode.Fill)

        assertEquals("the body", restored.message)
        assertEquals("typed", restored.subject)
    }

    @Test
    fun `filling a blank subject is what the quiet path is allowed to do`() {
        val restored = fromNewThread.restoredInto(Draft("", subject = ""), RestoreMode.Fill)

        assertEquals("the subject", restored.subject)
    }

    /** No dialog was shown, so nothing the writer typed may be thrown away. */
    @Test
    fun `filling never replaces a subject that is already there`() {
        val restored = fromNewThread.restoredInto(Draft("", subject = "typed"), RestoreMode.Fill)

        assertEquals("typed", restored.subject)
    }

    /** The dialog was answered with "overwrite" - that is the answer, subject included. */
    @Test
    fun `overwrite replaces the subject the message was written for`() {
        val current = Draft("half a thought", subject = "typed")

        val restored = fromNewThread.restoredInto(current, RestoreMode.Overwrite)

        assertEquals("the body", restored.message)
        assertEquals("the subject", restored.subject)
    }

    @Test
    fun `append leaves the subject alone`() {
        val current = Draft("half a thought", subject = "typed")

        val restored = fromNewThread.restoredInto(current, RestoreMode.Append)

        assertEquals("typed", restored.subject)
    }
    //endregion

    //region the message
    @Test
    fun `append puts the saved message on a line of its own`() {
        val restored = fromReply.restoredInto(Draft("half a thought"), RestoreMode.Append)

        assertEquals("half a thought\nthe body", restored.message)
    }

    @Test
    fun `append does not add a second newline to a draft that ends in one`() {
        val restored = fromReply.restoredInto(Draft("half a thought\n"), RestoreMode.Append)

        assertEquals("half a thought\nthe body", restored.message)
    }

    @Test
    fun `appending to nothing is the saved message on its own`() {
        val restored = fromReply.restoredInto(Draft(""), RestoreMode.Append)

        assertEquals("the body", restored.message)
    }
    //endregion

    //region the stores
    @Test
    fun `what was sent survives a new instance over the same store`() {
        val store = InMemoryKeyValueStore()
        StoredSentDraftRepository(store).save("the body", "the subject")

        assertEquals(SentDraft("the body", "the subject"), StoredSentDraftRepository(store).draft.value)
    }

    @Test
    fun `a reply is stored without a subject rather than with an empty one`() {
        val store = InMemoryKeyValueStore()
        StoredSentDraftRepository(store).save("the body", subject = null)

        assertNull(StoredSentDraftRepository(store).draft.value?.subject)
    }

    /** A subject typed as spaces is not a subject, and the restore rules key on null. */
    @Test
    fun `a blank subject is stored as none`() {
        val repository = InMemorySentDraftRepository()

        repository.save("the body", subject = "   ")

        assertNull(repository.draft.value?.subject)
    }

    @Test
    fun `there is nothing to restore before anything has been sent`() {
        assertNull(InMemorySentDraftRepository().draft.value)
        assertNull(StoredSentDraftRepository(InMemoryKeyValueStore()).draft.value)
    }

    @Test
    fun `the desktop store forgets when the process does`() {
        val repository = InMemorySentDraftRepository()
        repository.save("the body", null)

        assertEquals("the body", repository.draft.value?.message)
        assertNull(InMemorySentDraftRepository().draft.value)
    }
    //endregion
}
