package com.scurab.android.zumpareader.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.repository.SentDraft
import com.scurab.android.zumpareader.test.mock
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme

/**
 * Putting the last sent message back in the box.
 *
 * Both places that write to the forum have this - the post screen and the thread's reply panel - so
 * the button, the dialog and the four calls behind them live here rather than twice.
 *
 * Why it exists: the forum sometimes accepts a post, reports success and does nothing with it. The
 * field is cleared on the answer, so what was written is gone. See
 * [com.scurab.android.zumpareader.repository.SentDraftRepository].
 */
interface RestoreDraftEventHandler {
    /** The button beside the field. Fills a blank field outright, and asks about a written-in one. */
    fun onRestoreDraftClicked()

    fun onRestoreDraftDismissed()
    fun onRestoreDraftAppended()
    fun onRestoreDraftOverwritten()
}

/**
 * The button, as the field's trailing icon. Only there when there is something to put back - an
 * always-present button that usually does nothing is a worse thing to look at than no button.
 */
@Composable
fun RestoreDraftIcon(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = Icons.Filled.Replay,
            contentDescription = stringResource(R.string.restore_draft),
            tint = if (enabled) {
                AppTheme.colorScheme.context
            } else {
                AppTheme.colorScheme.contextTextDisabled
            },
        )
    }
}

/**
 * Shown only when the field already has something in it, because that is the only case with a
 * decision in it. The saved message is shown in full rather than summarised - deciding between
 * append and overwrite means reading what would arrive.
 */
@Composable
fun RestoreDraftDialog(draft: SentDraft, eventHandler: RestoreDraftEventHandler) {
    AlertDialog(
        onDismissRequest = eventHandler::onRestoreDraftDismissed,
        containerColor = AppTheme.colorScheme.primaryBackground,
        title = {
            Text(
                text = stringResource(R.string.restore_draft_title),
                style = AppTheme.typography.subject,
                color = AppTheme.colorScheme.context,
            )
        },
        text = {
            //a long message must not push the buttons off the screen
            Column(
                modifier = Modifier
                    .heightIn(max = MAX_PREVIEW_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spaces.small),
            ) {
                //only a new thread saved one, and only a new thread can use it - either way it is
                //worth seeing, because it says which of the two this draft was
                draft.subject?.let { subject ->
                    Text(
                        text = subject,
                        style = AppTheme.typography.subject,
                        color = AppTheme.colorScheme.primaryText,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = draft.message,
                    style = AppTheme.typography.message,
                    color = AppTheme.colorScheme.primaryText,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = eventHandler::onRestoreDraftOverwritten) {
                Text(
                    text = stringResource(R.string.restore_draft_overwrite),
                    color = AppTheme.colorScheme.context,
                )
            }
        },
        dismissButton = {
            //two buttons in the one slot: AlertDialog lays its button row out itself, so they are
            //emitted side by side rather than wrapped in a Row that would lose that spacing.
            //Cancel first - the two harmless answers together, away from overwrite.
            TextButton(onClick = eventHandler::onRestoreDraftDismissed) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = AppTheme.colorScheme.hint,
                )
            }
            TextButton(onClick = eventHandler::onRestoreDraftAppended) {
                Text(
                    text = stringResource(R.string.restore_draft_append),
                    color = AppTheme.colorScheme.context,
                )
            }
        },
    )
}

private val MAX_PREVIEW_HEIGHT = 240.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RestoreDraftDialogNewThreadPreview() = AppTheme {
    RestoreDraftDialog(
        draft = SentDraft(
            message = "Long enough to be worth not typing again, and to show the two lines.",
            subject = "A subject that was sent",
        ),
        eventHandler = mock(),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RestoreDraftDialogReplyPreview() = AppTheme {
    RestoreDraftDialog(
        draft = SentDraft(message = "An answer the forum swallowed.", subject = null),
        eventHandler = mock(),
    )
}
