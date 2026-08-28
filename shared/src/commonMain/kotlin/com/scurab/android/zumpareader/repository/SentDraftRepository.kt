package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.util.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What was handed to the forum the last time send was pressed.
 *
 * It exists because the forum sometimes answers a post with a success it did not act on - the reply
 * is accepted, reported as sent, and never appears. Whatever was written is gone by then: the field
 * is cleared on the answer, not on the message turning up. So the last thing sent is kept, and can
 * be typed back into the box rather than remembered and written again.
 *
 * @param subject only a new thread carries one - a reply's subject belongs to the thread, not to
 * the person answering it. Null means this was a reply.
 */
data class SentDraft(val message: String, val subject: String?)

/**
 * The last [SentDraft], as long as one has been sent.
 *
 * A `StateFlow` rather than a getter because the button that puts it back appears the moment there
 * is something to put back - including in a screen that was already open when it was sent.
 */
interface SentDraftRepository {

    val draft: StateFlow<SentDraft?>

    /** Called as send is pressed, not when the forum answers - the point is the answer is a lie. */
    fun save(message: String, subject: String?)
}

/**
 * What the desktop app gets: the draft lives as long as the process does.
 *
 * Deliberately not the file-backed [KeyValueStore] the desktop uses for its settings. This is a
 * safety net for the session you are in, and one that survived a restart would offer to restore
 * something written days ago.
 */
class InMemorySentDraftRepository : SentDraftRepository {

    private val _draft = MutableStateFlow<SentDraft?>(null)
    override val draft: StateFlow<SentDraft?> = _draft.asStateFlow()

    override fun save(message: String, subject: String?) {
        if (message.isBlank()) return
        _draft.value = SentDraft(message, subject?.takeIf { it.isNotBlank() })
    }
}

/**
 * What Android gets: the draft outlives the process, because the app being killed behind a browser
 * or a camera is one of the ways a message goes missing.
 *
 * The keys are private to this class rather than added to
 * [com.scurab.android.zumpareader.util.ZumpaPrefs] - a sent draft is not a setting, and nothing
 * else has any business reading it.
 */
class StoredSentDraftRepository(private val store: KeyValueStore) : SentDraftRepository {

    private val _draft = MutableStateFlow(read())
    override val draft: StateFlow<SentDraft?> = _draft.asStateFlow()

    override fun save(message: String, subject: String?) {
        if (message.isBlank()) return
        val saved = SentDraft(message, subject?.takeIf { it.isNotBlank() })
        store.putString(KEY_MESSAGE, saved.message)
        store.putString(KEY_SUBJECT, saved.subject)
        _draft.value = saved
    }

    private fun read(): SentDraft? = store.getString(KEY_MESSAGE, null)
        ?.takeIf { it.isNotBlank() }
        ?.let { SentDraft(it, store.getString(KEY_SUBJECT, null)) }

    private companion object {
        const val KEY_MESSAGE = "KEY_SENT_DRAFT_MESSAGE"
        const val KEY_SUBJECT = "KEY_SENT_DRAFT_SUBJECT"
    }
}

/**
 * How a saved draft goes back into the box.
 *
 * [Fill] is what happens with no dialog, because there is nothing there to lose. The other two are
 * the answer to one, and the difference between them is only what they do to what is already
 * written - see [SentDraft.restoredInto].
 */
enum class RestoreMode { Fill, Overwrite, Append }

/**
 * What is being written, before and after a restore.
 *
 * @param subject null means the subject is not the writer's to set - a reply into an existing
 * thread. It stays null on the way out, which is what "leave it alone" looks like to a caller.
 */
data class Draft(val message: String, val subject: String? = null)

/**
 * A saved draft put back into [current].
 *
 * The subject is the fiddly half, because the thing saved and the thing being written need not be
 * the same kind of post:
 *
 * - Writing a **reply**, the subject is the thread's. A saved subject is dropped.
 * - Writing a **new thread** from a draft saved as a reply, there is no saved subject to use, so
 *   the message is filled and the subject left as typed.
 * - Writing a **new thread** from one saved as a new thread, [Overwrite] replaces the subject too -
 *   it is the subject that message was written for. [Fill] only fills a blank one, so the quiet
 *   path cannot destroy anything, and [Append] never touches it: appending to a subject is not a
 *   thing, and a draft being added to already has one.
 */
fun SentDraft.restoredInto(current: Draft, mode: RestoreMode): Draft {
    val saved = this
    val message = when (mode) {
        RestoreMode.Append -> {
            val separator =
                if (current.message.isEmpty() || current.message.endsWith("\n")) "" else "\n"
            "${current.message}$separator${saved.message}"
        }

        RestoreMode.Fill, RestoreMode.Overwrite -> saved.message
    }
    val subject = when {
        //a reply: the thread owns the subject, whatever was saved
        current.subject == null -> null
        //saved from a reply, so there is no subject to offer
        saved.subject == null -> current.subject
        mode == RestoreMode.Overwrite -> saved.subject
        mode == RestoreMode.Fill && current.subject.isBlank() -> saved.subject
        else -> current.subject
    }
    return Draft(message, subject)
}
