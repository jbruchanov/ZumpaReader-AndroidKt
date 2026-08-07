package com.scurab.android.zumpareader.ui.post

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.CopyToClipboard
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.test.Fixtures
import com.scurab.android.zumpareader.test.busy
import com.scurab.android.zumpareader.test.fresh
import com.scurab.android.zumpareader.test.mock
import com.scurab.android.zumpareader.test.resized
import com.scurab.android.zumpareader.test.uploaded
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import com.scurab.android.zumpareader.util.saveToClipboard
import org.koin.androidx.compose.koinViewModel

/**
 * [onLinkUploaded] is how a finished upload reaches the message draft. The host passes
 * `postViewModel::onLinkShared`; in C4 that becomes the enclosing [PostScreen] doing the same.
 */
@Composable
fun PostImageScreen(
    uri: Uri,
    onLinkUploaded: (String) -> Unit,
    vm: PostImageViewModel = koinViewModel(),
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is PostImageEffect.ImageUploaded -> {
                    onLinkUploaded(effect.link)
                    context.toast(R.string.done)
                }

                is CopyToClipboard -> {
                    context.saveToClipboard(effect.text.toString())
                    context.toast(R.string.saved_into_clipboard)
                }

                is ShowToast -> effect.text?.let { context.toast(it) } ?: context.toast(effect.resId)
                else -> Unit
            }
        }
    }
    LaunchedEffect(uri) { vm.start(uri) }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val eventHandler = vm
    PostImageScreen(uiState, eventHandler)
}

@Composable
private fun PostImageScreen(uiState: PostImageUiState, eventHandler: PostImageEventHandler) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.primaryBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(AppTheme.spaces.listItemPadding),
            contentAlignment = Alignment.Center,
        ) {
            ImagePreview(uiState)
            if (uiState.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AppTheme.sizes.progressBar),
                    color = AppTheme.colorScheme.context,
                )
            }
        }
        ImagePanel(uiState, eventHandler)
    }
}

/**
 * The thumbnail. A file path today because `CopyFromResourcesTask` writes one - it becomes an
 * `AsyncImage` model when Coil lands in C9, which is also when the rotation stops needing a
 * graphicsLayer of its own.
 */
@Composable
private fun ImagePreview(uiState: PostImageUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { rotationZ = uiState.rotationDegrees.toFloat() },
        contentAlignment = Alignment.Center,
    ) {
        if (uiState.thumbnailPath == null) {
            Text(
                text = stringResource(R.string.wheeeee),
                style = AppTheme.typography.author,
                color = AppTheme.colorScheme.primaryText,
            )
        } else {
            //placeholder until Coil - see C9
            Text(
                text = uiState.thumbnailPath.substringAfterLast('/'),
                style = AppTheme.typography.author,
                color = AppTheme.colorScheme.context,
            )
        }
    }
}

@Composable
private fun ImagePanel(uiState: PostImageUiState, eventHandler: PostImageEventHandler) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colorScheme.secondaryBackground)
            .padding(AppTheme.spaces.normal),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spaces.small),
    ) {
        ImageMetaRow(
            label = stringResource(R.string.original),
            meta = uiState.original,
        )
        ImageMetaRow(
            label = stringResource(R.string.resized),
            meta = uiState.resized,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SampleSizeSelector(uiState, eventHandler)
            PanelIcon(R.drawable.ic_resize, !uiState.isBusy, eventHandler::onResizeClicked)
            PanelIcon(R.drawable.ic_rotate_right, !uiState.isBusy, eventHandler::onRotateClicked)
            if (uiState.uploadedLink != null) {
                PanelIcon(R.drawable.ic_copy, true, eventHandler::onCopyLinkClicked)
            }
            PanelIcon(R.drawable.ic_send, !uiState.isBusy, eventHandler::onUploadClicked)
        }
    }
}

@Composable
private fun ImageMetaRow(label: String, meta: ImageMetaUiState?) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppTheme.typography.tableHeader,
            color = AppTheme.colorScheme.context,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = meta?.let { ImageMetaFormat.resolution(it.width, it.height) }.orEmpty(),
            style = AppTheme.typography.tableText,
            color = AppTheme.colorScheme.context,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = meta?.let { ImageMetaFormat.size(it.bytes) }.orEmpty(),
            style = AppTheme.typography.tableText,
            color = AppTheme.colorScheme.context,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleSizeSelector(uiState: PostImageUiState, eventHandler: PostImageEventHandler) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.width(SELECTOR_WIDTH),
    ) {
        TextField(
            value = ImageMetaFormat.sampleLabels[uiState.sampleSizeIndex],
            onValueChange = {},
            readOnly = true,
            textStyle = AppTheme.typography.button,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ImageMetaFormat.sampleLabels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label, style = AppTheme.typography.button) },
                    onClick = {
                        expanded = false
                        eventHandler.onSampleSizeSelected(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun PanelIcon(iconRes: Int, enabled: Boolean, onClick: () -> Unit) {
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

private val SELECTOR_WIDTH = 96.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 400)
@Composable
private fun PostImageFreshPreview() = AppTheme {
    PostImageScreen(Fixtures.PostImage.fresh(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 400)
@Composable
private fun PostImageResizedPreview() = AppTheme {
    PostImageScreen(Fixtures.PostImage.resized(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 400)
@Composable
private fun PostImageUploadedPreview() = AppTheme {
    PostImageScreen(Fixtures.PostImage.uploaded(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 400)
@Composable
private fun PostImageBusyPreview() = AppTheme {
    PostImageScreen(Fixtures.PostImage.busy(), mock())
}
