package com.scurab.android.zumpareader.ui.offline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.test.Fixtures
import com.scurab.android.zumpareader.test.finished
import com.scurab.android.zumpareader.test.idle
import com.scurab.android.zumpareader.test.running
import com.scurab.android.zumpareader.test.mock
import com.scurab.android.zumpareader.ui.compose.LocalNavigator
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun OfflineDownloadScreen(vm: OfflineDownloadViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is OfflineDownloadEffect.Dismiss -> navigator.back()
            }
        }
    }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val eventHandler = vm
    OfflineDownloadScreen(uiState, eventHandler)
}

@Composable
private fun OfflineDownloadScreen(
    uiState: OfflineDownloadUiState,
    eventHandler: OfflineDownloadEventHandler,
) {
    //the dialog refuses to close mid-download, exactly as the key listener did
    BackHandler(enabled = !uiState.isDismissable) { }

    Column(
        modifier = Modifier
            .widthIn(min = AppTheme.sizes.dialogOfflineMinWidth)
            //a frame, so the dialog is not black on black - the xml used ?buttonBackground
            .background(AppTheme.colorScheme.secondaryBackground, AppTheme.shapes.button)
            .border(
                AppTheme.sizes.urlButtonStrokeWidth,
                AppTheme.colorScheme.context50p,
                AppTheme.shapes.button,
            )
            .padding(AppTheme.spaces.normal),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spaces.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = uiState.downloadImages,
                onCheckedChange = eventHandler::onDownloadImagesToggled,
                enabled = !uiState.isRunning,
                colors = CheckboxDefaults.colors(
                    checkedColor = AppTheme.colorScheme.context,
                    uncheckedColor = AppTheme.colorScheme.primaryText,
                    checkmarkColor = AppTheme.colorScheme.primaryBackground,
                ),
            )
            Text(
                text = stringResource(R.string.download_images),
                style = AppTheme.typography.threads,
                color = AppTheme.colorScheme.primaryText,
            )
        }

        Text(
            text = stringResource(R.string.pages),
            style = AppTheme.typography.threads,
            color = AppTheme.colorScheme.primaryText,
        )
        OutlinedTextField(
            value = uiState.pages,
            onValueChange = { if (it.length <= PAGES_MAX_LENGTH) eventHandler.onPagesChanged(it) },
            enabled = !uiState.isRunning,
            singleLine = true,
            textStyle = AppTheme.typography.threads.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = AppTheme.shapes.editText,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        //a thread body is a request of its own now, so this counts up rather than landing at once
        CounterRow(
            label = stringResource(R.string.threads),
            value = "%s/%s".format(uiState.threadsDownloaded, uiState.threadsTotal),
        )
        CounterRow(
            label = stringResource(R.string.images),
            value = "%s/%s".format(uiState.imagesDownloaded, uiState.imagesTotal),
        )

        //space is reserved either way so the buttons do not jump when it appears
        if (uiState.isRunning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppTheme.spaces.small),
                color = AppTheme.colorScheme.context,
                trackColor = AppTheme.colorScheme.secondaryBackground,
            )
        } else {
            Spacer(Modifier.fillMaxWidth().height(AppTheme.spaces.small))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spaces.normal),
        ) {
            TextButton(
                onClick = eventHandler::onStartClicked,
                enabled = uiState.canStart,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.action_start),
                    style = AppTheme.typography.button,
                    color = if (uiState.canStart) {
                        AppTheme.colorScheme.buttonText
                    } else {
                        AppTheme.colorScheme.contextTextDisabled
                    },
                )
            }
            TextButton(
                onClick = eventHandler::onStopClicked,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.action_stop),
                    style = AppTheme.typography.button,
                    color = AppTheme.colorScheme.buttonText,
                )
            }
        }
    }
}

@Composable
private fun CounterRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppTheme.typography.threads,
            color = AppTheme.colorScheme.primaryText,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = AppTheme.typography.threads,
            color = AppTheme.colorScheme.primaryText,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val PAGES_MAX_LENGTH = 1

@Preview
@Composable
private fun OfflineDownloadIdlePreview() = AppTheme {
    OfflineDownloadScreen(Fixtures.OfflineDownload.idle(), mock())
}

@Preview
@Composable
private fun OfflineDownloadRunningPreview() = AppTheme {
    OfflineDownloadScreen(Fixtures.OfflineDownload.running(), mock())
}

@Preview
@Composable
private fun OfflineDownloadFinishedPreview() = AppTheme {
    OfflineDownloadScreen(Fixtures.OfflineDownload.finished(), mock())
}
