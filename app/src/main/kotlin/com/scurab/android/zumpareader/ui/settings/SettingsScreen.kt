package com.scurab.android.zumpareader.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.CopyToClipboard
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.test.Fixtures
import com.scurab.android.zumpareader.test.busy
import com.scurab.android.zumpareader.test.loggedIn
import com.scurab.android.zumpareader.test.loggedOut
import com.scurab.android.zumpareader.test.mock
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import com.scurab.android.zumpareader.util.saveToClipboard
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel = koinViewModel()) {
    val context = LocalContext.current
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.onResumed() }

    //the permission can be changed outside the app, so it is re-read on every return to the screen
    LifecycleResumeEffect(Unit) {
        vm.onResumed()
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.RequestNotificationPermission ->
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)

                is SettingsEffect.OpenAppSettings -> context.openAppSettings()
                is CopyToClipboard -> {
                    context.saveToClipboard(effect.text.toString())
                    context.toast("'${effect.text}' saved to clipboard")
                }

                is ShowToast -> effect.text?.let { context.toast(it) } ?: context.toast(effect.resId)
                else -> Unit
            }
        }
    }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val eventHandler = vm
    SettingsScreen(uiState, eventHandler)
}

/** Where the user goes to grant a notification permission that was denied for good. */
private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    )
}

@Composable
private fun SettingsScreen(uiState: SettingsUiState, eventHandler: SettingsEventHandler) {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.primaryBackground)
            //no Scaffold on this screen, so the insets are applied here
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.spaces.normal),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spaces.small),
        ) {
            SectionHeader(stringResource(R.string.credentials))
            OutlinedTextField(
                value = uiState.userName,
                onValueChange = eventHandler::onUserNameChanged,
                enabled = !uiState.isBusy,
                label = { Text(stringResource(R.string.user)) },
                singleLine = true,
                shape = AppTheme.shapes.editText,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = eventHandler::onPasswordChanged,
                enabled = !uiState.isBusy,
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = AppTheme.shapes.editText,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    if (uiState.isLoggedIn) eventHandler.onLogoutClicked() else eventHandler.onLoginClicked()
                },
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(if (uiState.isLoggedIn) R.string.logout else R.string.login),
                    style = AppTheme.typography.button,
                    color = AppTheme.colorScheme.buttonText,
                )
            }

            HorizontalDivider(color = AppTheme.colorScheme.context25p)
            SectionHeader(stringResource(R.string.settings))
            FilterSelector(uiState, eventHandler)
            OutlinedTextField(
                value = uiState.nick,
                onValueChange = eventHandler::onNickChanged,
                enabled = !uiState.isBusy,
                label = { Text(stringResource(R.string.nick_name)) },
                singleLine = true,
                shape = AppTheme.shapes.editText,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(
                label = stringResource(R.string.load_images),
                checked = uiState.loadImages,
                enabled = !uiState.isBusy,
                onCheckedChange = eventHandler::onLoadImagesToggled,
            )
            SwitchRow(
                label = stringResource(R.string.offline),
                checked = uiState.isOffline,
                enabled = !uiState.isBusy,
                onCheckedChange = eventHandler::onOfflineToggled,
            )
            SwitchRow(
                label = stringResource(R.string.show_last_author),
                checked = uiState.showLastAuthor,
                enabled = uiState.isSessionOnlyEnabled,
                onCheckedChange = eventHandler::onShowLastAuthorToggled,
            )

            HorizontalDivider(color = AppTheme.colorScheme.context25p)
            SectionHeader(stringResource(R.string.permissions))
            ClickRow(
                label = stringResource(R.string.notifications),
                value = stringResource(
                    if (uiState.areNotificationsEnabled) R.string.enabled else R.string.disabled
                ),
                onClick = eventHandler::onNotificationsClicked,
            )

            HorizontalDivider(color = AppTheme.colorScheme.context25p)
            SectionHeader(stringResource(R.string.crashlytics))
            ClickRow(
                label = uiState.userId.orEmpty(),
                value = "",
                onClick = eventHandler::onUserIdClicked,
            )
        }

        if (uiState.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(AppTheme.sizes.progressBar),
                color = AppTheme.colorScheme.context,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.subject,
        color = AppTheme.colorScheme.context,
        modifier = Modifier.padding(top = AppTheme.spaces.normal),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spaces.tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AppTheme.typography.threads,
            color = if (enabled) {
                AppTheme.colorScheme.primaryText
            } else {
                AppTheme.colorScheme.hint
            },
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppTheme.colorScheme.context,
                checkedTrackColor = AppTheme.colorScheme.context25p,
            ),
        )
    }
}

@Composable
private fun ClickRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppTheme.spaces.normal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AppTheme.typography.threads,
            color = AppTheme.colorScheme.primaryText,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = AppTheme.typography.author,
                color = AppTheme.colorScheme.context,
            )
        }
    }
}

@Composable
private fun FilterSelector(uiState: SettingsUiState, eventHandler: SettingsEventHandler) {
    var expanded by remember { mutableStateOf(false) }
    val labels = stringArrayResource(R.array.filter_labels)
    val values = stringArrayResource(R.array.filter_values)
    val index = values.indexOf(uiState.filter).coerceAtLeast(0)

    Box {
        ClickRow(
            label = stringResource(R.string.filter),
            value = labels.getOrElse(index) { "" },
            onClick = { if (uiState.isSessionOnlyEnabled) expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        eventHandler.onFilterChanged(values[i])
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun SettingsScreenLoggedOutPreview() = AppTheme {
    SettingsScreen(Fixtures.Settings.loggedOut(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun SettingsScreenLoggedInPreview() = AppTheme {
    SettingsScreen(Fixtures.Settings.loggedIn(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun SettingsScreenBusyPreview() = AppTheme {
    SettingsScreen(Fixtures.Settings.busy(), mock())
}
