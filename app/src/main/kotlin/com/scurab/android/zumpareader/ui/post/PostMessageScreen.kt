package com.scurab.android.zumpareader.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.test.Fixtures
import com.scurab.android.zumpareader.test.mock
import com.scurab.android.zumpareader.test.newThread
import com.scurab.android.zumpareader.test.reply
import com.scurab.android.zumpareader.test.sending
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.primaryBackground)
            .imePadding()
            .padding(AppTheme.spaces.tiny),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spaces.tiny),
    ) {
        if (uiState.isSubjectEditable) {
            OutlinedTextField(
                value = uiState.subject,
                onValueChange = eventHandler::onSubjectChanged,
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

        OutlinedTextField(
            value = uiState.message,
            onValueChange = eventHandler::onMessageChanged,
            enabled = !uiState.isSending,
            label = { Text(stringResource(R.string.message)) },
            textStyle = AppTheme.typography.message,
            shape = AppTheme.shapes.editText,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = AppTheme.sizes.newMessageEditTextMinHeight),
        )

        PostActionsRow(uiState, eventHandler)
    }
}

@Composable
private fun PostActionsRow(uiState: PostUiState, eventHandler: PostMessageEventHandler) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ActionIcon(R.drawable.ic_photo, enabled = !uiState.isSending, onClick = eventHandler::onPhotoClicked)
        ActionIcon(R.drawable.ic_camera, enabled = !uiState.isSending, onClick = eventHandler::onCameraClicked)
        Spacer(Modifier.weight(1f))
        ActionIcon(R.drawable.ic_send, enabled = uiState.canSend, onClick = eventHandler::onSendClicked)
    }
}

@Composable
private fun ActionIcon(iconRes: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) {
                AppTheme.colorScheme.context
            } else {
                AppTheme.colorScheme.contextTextDisabled
            },
        )
    }
}

private const val SUBJECT_MAX_LINES = 5

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
