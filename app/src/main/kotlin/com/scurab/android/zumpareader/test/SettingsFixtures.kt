package com.scurab.android.zumpareader.test

import com.scurab.android.zumpareader.ui.settings.SettingsUiState

fun Fixtures.Settings.loggedOut() = SettingsUiState(
    userName = "",
    password = "",
    nick = "",
    userId = "0f2c9a44-51d1-4f7e-9f2a-1c7f0f3a9b21",
)

fun Fixtures.Settings.loggedIn() = SettingsUiState(
    userName = "honza",
    password = "hunter2",
    nick = "Honza",
    filter = "1",
    isLoggedIn = true,
    loadImages = true,
    showLastAuthor = true,
    areNotificationsEnabled = true,
    userId = "honza",
)

fun Fixtures.Settings.busy() = loggedOut().copy(userName = "honza", password = "x", isBusy = true)
