package com.scurab.zumpareader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What is being written and where it goes: a new thread from the list, or an answer to the thread
 * that is on screen.
 */
internal sealed interface Composing {
    data object NewThread : Composing
    data class Reply(val threadId: String, val subject: String) : Composing
}

/**
 * The way to write something, at the bottom of whichever pane it belongs to rather than behind a
 * button.
 *
 * This is the shape the phone uses for a reply - the field on a line of its own, the actions in a
 * row under it - and it is up the whole time, so writing does not start with opening anything. On a
 * desktop that costs nothing: there is height to spare and a keyboard already under the reader's
 * hands.
 *
 * A subject field only when there is no thread to answer. A reply carries the subject of the thread
 * it belongs to, which is why [Composing.Reply] holds one rather than asking for it.
 *
 * No image or camera actions, unlike the row on Android. There is no camera on a desktop, and
 * picking a file would mean an upload path this module has not got - `ImageUploadRepository` is in
 * `:shared`, but nothing here is wired to it and half a picker is worse than none.
 *
 * The draft is keyed on [target], so moving to another thread starts a new one - and coming back to
 * a thread whose id and subject are unchanged does not, because [Composing.Reply] is a data class.
 */
@Composable
internal fun WritePanel(
    target: Composing,
    isSending: Boolean,
    onSend: (subject: String, message: String) -> Unit,
) {
    var subject by remember(target) { mutableStateOf("") }
    var message by remember(target) { mutableStateOf("") }
    val isNewThread = target is Composing.NewThread
    val canSend = !isSending && message.isNotBlank() && (!isNewThread || subject.isNotBlank())

    fun send() {
        if (canSend) onSend(subject, message)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isNewThread) {
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                enabled = !isSending,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().sendOnAltEnter(::send),
            )
        }
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text(if (isNewThread) "Message" else "Reply") },
            enabled = !isSending,
            maxLines = MESSAGE_MAX_LINES,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MESSAGE_MIN_HEIGHT)
                .sendOnAltEnter(::send),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isSending) "Sending..." else "Alt+Enter to send",
                color = Color.Gray,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = ::send, enabled = canSend) {
                Text(
                    text = if (isNewThread) "Post" else "Reply",
                    color = if (canSend) Accent else Color.Gray,
                )
            }
        }
    }
}

/**
 * Alt+Enter sends; Enter on its own is a newline, which is what a multi-line field is for.
 *
 * Previewed rather than handled, so the key never reaches the field and no newline is inserted on
 * the way through. Key down only - a press arrives as a down and an up, and acting on both would
 * post twice.
 */
private fun Modifier.sendOnAltEnter(onSend: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.isAltPressed && event.key == Key.Enter) {
        onSend()
        true
    } else {
        false
    }
}

/** Room for a few lines without the panel growing by one every time a line is added. */
private val MESSAGE_MIN_HEIGHT = 96.dp

private const val MESSAGE_MAX_LINES = 8
