package com.scurab.zumpareader.desktop


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What to write and where it goes: a new thread from the list, or an answer to the thread that is
 * on screen.
 */
internal sealed interface Composing {
    data object NewThread : Composing
    data class Reply(val threadId: String, val subject: String) : Composing
}

/**
 * Writing something, as a dialog.
 *
 * No image or camera buttons, unlike the post screen on Android. There is no camera on a desktop,
 * and picking a file would mean an upload path this module does not have - `ImageUploadRepository`
 * is in `:shared`, but nothing here is wired to it and half a picker is worse than none. Text is
 * what a desktop keyboard is for.
 *
 * A subject only when there is no thread to answer: a reply carries the subject of the thread it
 * belongs to, which is why [Composing.Reply] holds one rather than asking for it.
 */
@Composable
internal fun ComposeDialog(
    composing: Composing,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: (subject: String, message: String) -> Unit,
) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val isNewThread = composing is Composing.NewThread
    val canSend = message.isNotBlank() && (!isNewThread || subject.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = {
            Text(
                when (composing) {
                    is Composing.NewThread -> "New thread"
                    is Composing.Reply -> "Reply to ${composing.subject}"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNewThread) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        enabled = !isSending,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    enabled = !isSending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MESSAGE_MIN_HEIGHT),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(subject, message) },
                enabled = canSend && !isSending,
            ) { Text(if (isSending) "Sending..." else "Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Cancel") }
        },
    )
}

/**
 * The way in to writing something, only there when there is something to write with - see the
 * condition at the call site.
 *
 * A plus for a new thread and `Re` for an answer. `:appAndroid` puts the same plus on both of its
 * fabs, but it has one per screen; this one fab means two different things depending on which pane
 * is showing, so the label is all that says which is on offer.
 */
@Composable
internal fun WriteFab(isReply: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = Accent,
        contentColor = Color.White,
        shape = CircleShape,
    ) {
        Text(text = if (isReply) "Re" else "+", fontSize = 20.sp)
    }
}

/** Room for a few lines without the dialog resizing on every newline. */
private val MESSAGE_MIN_HEIGHT = 120.dp
