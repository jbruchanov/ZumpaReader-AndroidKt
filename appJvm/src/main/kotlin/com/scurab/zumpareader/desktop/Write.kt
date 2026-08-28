package com.scurab.zumpareader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
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
 * The reply box, at the bottom of the thread and up the whole time.
 *
 * This is the shape the phone uses - the field on a line of its own, the actions in a row under
 * it - and it belongs to a thread, so it only exists where a thread does. The list has a fab and a
 * dialog instead: a write box permanently under a list of threads is a box with nothing to say.
 *
 * No image or camera actions, unlike the row on Android. There is no camera on a desktop, and
 * picking a file would mean an upload path this module has not got - `ImageUploadRepository` is in
 * `:shared`, but nothing here is wired to it and half a picker is worse than none.
 *
 * The draft is keyed on [target], so moving to another thread starts a new one - and coming back to
 * a thread whose id and subject are unchanged does not, because [Composing.Reply] is a data class.
 */
@Composable
internal fun ReplyPanel(
    target: Composing.Reply,
    isSending: Boolean,
    onSend: (message: String) -> Unit,
) {
    var message by remember(target) { mutableStateOf("") }
    val canSend = !isSending && message.isNotBlank()

    fun send() {
        if (canSend) onSend(message)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Reply") },
            enabled = !isSending,
            maxLines = MESSAGE_MAX_LINES,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MESSAGE_MIN_HEIGHT)
                .sendOnAltEnter(::send),
        )
        SendRow(isSending = isSending, canSend = canSend, label = "Reply", onSend = ::send)
    }
}

/**
 * A new thread, as a dialog behind the list's fab - which is what the phone does with it too, the
 * post screen being a screen of its own rather than something under the list.
 */
@Composable
internal fun NewThreadDialog(
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: (subject: String, message: String) -> Unit,
) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val canSend = !isSending && subject.isNotBlank() && message.isNotBlank()

    fun send() {
        if (canSend) onSend(subject, message)
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        containerColor = Background,
        title = { Text("New thread", color = Accent) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    enabled = !isSending,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().sendOnAltEnter(::send),
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    enabled = !isSending,
                    maxLines = MESSAGE_MAX_LINES,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MESSAGE_MIN_HEIGHT)
                        .sendOnAltEnter(::send),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::send, enabled = canSend) {
                Text(
                    text = if (isSending) "Sending..." else "Post",
                    color = if (canSend) Accent else Muted,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Cancel", color = Muted) }
        },
    )
}

/** The way in to a new thread, over the list. Only there when there is an account to write with. */
@Composable
internal fun WriteFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = Accent,
        contentColor = Content,
        shape = CircleShape,
    ) {
        //a label rather than an icon: the material icon artifacts are an Android-app dependency
        //this module does not carry, which is why the overflow menu is worded too
        Text(text = "+", fontSize = 22.sp)
    }
}

@Composable
private fun SendRow(isSending: Boolean, canSend: Boolean, label: String, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (isSending) "Sending..." else "Alt+Enter to send",
            color = Muted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSend, enabled = canSend) {
            Text(text = label, color = if (canSend) Accent else Muted)
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

/** Room for a few lines without the box growing by one every time a line is added. */
private val MESSAGE_MIN_HEIGHT = 96.dp

private const val MESSAGE_MAX_LINES = 8
