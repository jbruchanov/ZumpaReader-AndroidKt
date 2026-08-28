package com.scurab.android.zumpareader.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.WindowLayout
import com.scurab.android.zumpareader.test.Fixtures
import com.scurab.android.zumpareader.test.mock
import com.scurab.android.zumpareader.test.newThread
import com.scurab.android.zumpareader.test.reply
import com.scurab.android.zumpareader.test.sending
import com.scurab.android.zumpareader.ui.compose.ActionIcon
import com.scurab.android.zumpareader.ui.compose.RestoreDraftDialog
import com.scurab.android.zumpareader.ui.compose.RestoreDraftIcon
import com.scurab.android.zumpareader.ui.compose.rememberFieldValue
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import org.koin.androidx.compose.koinViewModel

/**
 * The message tab of [PostScreen]. Binds to the shared [PostViewModel] rather than owning one - the
 * subject and the message are the dialog's state, and the image tabs append their uploaded links to
 * the same draft.
 */
@Composable
fun PostMessageScreen(vm: PostViewModel = koinViewModel()) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val eventHandler = vm
    PostMessageScreen(uiState, eventHandler)
}

@Composable
private fun PostMessageScreen(uiState: PostUiState, eventHandler: PostMessageEventHandler) {
    /*
     * A short window - a phone in landscape - has nothing like the height this screen wants once
     * the keyboard is up, so below the threshold it stops trying to fit and scrolls instead. The
     * action row scrolls with it rather than staying pinned: pinning it would take its height out
     * of the little that is left, which is the height the fields need.
     *
     * Held outside the branch so the position survives a rotation either way - rememberScrollState
     * saves itself, but only if it is still called.
     */
    val isCompactHeight = LocalConfiguration.current.screenHeightDp < WindowLayout.COMPACT_HEIGHT_DP
    val scrollState = rememberScrollState()
    //outside the branch and outside the `if` below, so the slots stay put and the carets restore
    val subjectValue = rememberFieldValue(uiState.subject)
    val messageValue = rememberFieldValue(uiState.message)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.primaryBackground)
            //the bottom half of what PostScreen left unconsumed. safeDrawing takes the larger of the
            //keyboard and the navigation bar, so the send row clears whichever is on screen - plain
            //imePadding() only knew about the keyboard and let the navigation bar sit on top of it
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            //after the inset and before the padding: the viewport is the space above the keyboard,
            //and the padding travels with the content so the last row can scroll clear of the edge
            .then(if (isCompactHeight) Modifier.verticalScroll(scrollState) else Modifier)
            .padding(AppTheme.spaces.tiny),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spaces.tiny),
    ) {
        if (uiState.isSubjectEditable) {
            OutlinedTextField(
                value = subjectValue.value,
                onValueChange = {
                    subjectValue.value = it
                    eventHandler.onSubjectChanged(it.text)
                },
                enabled = !uiState.isSending,
                label = { Text(stringResource(R.string.subject)) },
                textStyle = AppTheme.typography.message,
                shape = AppTheme.shapes.editText,
                maxLines = SUBJECT_MAX_LINES,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppTheme.sizes.responseEditTextMinHeight),
            )
        }

        //A short window puts the field and the buttons beside each other instead of stacking them,
        //because the height a second row costs is the height the field has left. Taller than that
        //and they stack, with the field taking the slack.
        if (isCompactHeight) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                MessageField(
                    value = messageValue,
                    uiState = uiState,
                    eventHandler = eventHandler,
                    lines = COMPACT_MESSAGE_LINES,
                    //the field takes the slack, so the buttons need no spacer to sit at the end
                    modifier = Modifier.weight(1f),
                )
                PostActionIcons(uiState, eventHandler, spaced = false)
            }
        } else {
            MessageField(
                value = messageValue,
                uiState = uiState,
                eventHandler = eventHandler,
                lines = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = AppTheme.sizes.newMessageEditTextMinHeight),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                PostActionIcons(uiState, eventHandler, spaced = true)
            }
        }
    }

    uiState.restorePrompt?.let { RestoreDraftDialog(it, eventHandler) }
}

/**
 * @param lines fixes the field at that many lines - min as well as max, or it would still grow a
 * line at a time and push whatever is beside or below it around. Null lets it grow.
 */
@Composable
private fun MessageField(
    value: MutableState<TextFieldValue>,
    uiState: PostUiState,
    eventHandler: PostMessageEventHandler,
    lines: Int?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value.value,
        onValueChange = {
            value.value = it
            eventHandler.onMessageChanged(it.text)
        },
        enabled = !uiState.isSending,
        label = { Text(stringResource(R.string.message)) },
        textStyle = AppTheme.typography.message,
        shape = AppTheme.shapes.editText,
        minLines = lines ?: 1,
        maxLines = lines ?: Int.MAX_VALUE,
        modifier = modifier,
    )
}

/**
 * @param spaced pushes send to the far end. Wanted when the icons are a row of their own, not when
 * something beside them already takes the slack.
 */
@Composable
private fun RowScope.PostActionIcons(
    uiState: PostUiState,
    eventHandler: PostMessageEventHandler,
    spaced: Boolean,
) {
    ActionIcon(
        icon = rememberVectorPainter(Icons.Filled.Photo),
        enabled = !uiState.isSending,
        onClick = eventHandler::onPhotoClicked,
    )
    ActionIcon(
        icon = rememberVectorPainter(Icons.Filled.PhotoCamera),
        enabled = !uiState.isSending,
        onClick = eventHandler::onCameraClicked,
    )
    //with the others rather than in the field: this screen has the room for a row of actions, and
    //the reply panel - which has not - is the one that keeps it as a trailing icon. Only once
    //something has been sent; see RestoreDraft.kt for what it is for.
    if (uiState.sentDraft != null) {
        RestoreDraftIcon(
            enabled = !uiState.isSending,
            onClick = eventHandler::onRestoreDraftClicked,
        )
    }
    if (spaced) Spacer(Modifier.weight(1f))
    ActionIcon(
        icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Send),
        enabled = uiState.canSend,
        onClick = eventHandler::onSendClicked,
    )
}

private const val SUBJECT_MAX_LINES = 5

/** What "locked to two lines" is - see the message field. */
private const val COMPACT_MESSAGE_LINES = 2

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 400)
@Composable
private fun PostMessageNewThreadPreview() = AppTheme {
    PostMessageScreen(Fixtures.Post.newThread(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 400)
@Composable
private fun PostMessageReplyPreview() = AppTheme {
    PostMessageScreen(Fixtures.Post.reply(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 400)
@Composable
private fun PostMessageSendingPreview() = AppTheme {
    PostMessageScreen(Fixtures.Post.sending(), mock())
}

/** A phone in landscape: field and buttons share a row, and the lot scrolls. */
@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 740, heightDp = 300)
@Composable
private fun PostMessageCompactHeightPreview() = AppTheme {
    PostMessageScreen(Fixtures.Post.newThread(), mock())
}
