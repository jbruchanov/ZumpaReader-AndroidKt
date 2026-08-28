package com.scurab.zumpareader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
 * The draft is held by the caller, not here: clearing it on a successful send needs to know whether
 * the forum took the reply, and this does not.
 */
@Composable
internal fun ReplyPanel(
    target: Composing.Reply,
    message: String,
    isSending: Boolean,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = !isSending && message.isNotBlank()

    fun send() {
        if (canSend) onSend()
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            label = { Text("Reply") },
            enabled = !isSending,
            maxLines = MESSAGE_MAX_LINES,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MESSAGE_MIN_HEIGHT)
                .sendOnAltEnter(::send),
        )
        SendRow(isSending = isSending, canSend = canSend, onSend = ::send)
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
        /*
         * Drawn, not written. A `Text("+")` sits on a text baseline and is sized by the font's
         * metrics, so it came out small and a little above centre - the glyph's ink is nowhere near
         * the middle of the box the font reserves for it. Two lines through the middle of a known
         * box have neither problem, and there is no icon artifact to reach for: the material icons
         * are an Android-app dependency this module does not carry, which is why the overflow menu
         * is worded rather than iconed too.
         */
        Canvas(Modifier.size(FAB_ICON_SIZE)) {
            val mid = size.minDimension / 2f
            val arm = size.minDimension / 2f
            drawLine(
                color = Content,
                start = Offset(mid - arm, mid),
                end = Offset(mid + arm, mid),
                strokeWidth = FAB_ICON_STROKE.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Content,
                start = Offset(mid, mid - arm),
                end = Offset(mid, mid + arm),
                strokeWidth = FAB_ICON_STROKE.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SendRow(isSending: Boolean, canSend: Boolean, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (isSending) "Sending..." else "Alt+Enter to send",
            color = Muted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSend, enabled = canSend) {
            SendGlyph(tint = if (canSend) Accent else Muted)
        }
    }
}

/**
 * `Icons.AutoMirrored.Filled.Send` is what the phone puts here, and this is that shape drawn by
 * hand: the material icon artifacts are an Android-app dependency this module does not carry, which
 * is why the fab's plus is drawn too.
 *
 * The paper dart is one path - the arrow's outline with the notch cut into its tail - scaled off
 * the canvas, so the size is the only thing to change.
 */
@Composable
private fun SendGlyph(tint: Color) {
    Canvas(Modifier.size(SEND_ICON_SIZE)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, h / 2f)
            lineTo(0f, h)
            //the notch, which is what makes it a dart rather than a triangle
            lineTo(w * 0.28f, h / 2f)
            close()
        }
        drawPath(path, color = tint)
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

private val SEND_ICON_SIZE = 18.dp
private val FAB_ICON_SIZE = 20.dp
private val FAB_ICON_STROKE = 2.dp

/** Room for a few lines without the box growing by one every time a line is added. */
private val MESSAGE_MIN_HEIGHT = 96.dp

private const val MESSAGE_MAX_LINES = 8
