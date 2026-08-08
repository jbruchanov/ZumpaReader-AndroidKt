package com.scurab.android.zumpareader.test

import com.scurab.android.zumpareader.ui.offline.OfflineDownloadUiState

fun Fixtures.OfflineDownload.idle() = OfflineDownloadUiState()

fun Fixtures.OfflineDownload.running() = OfflineDownloadUiState(
    pages = "3",
    downloadImages = true,
    threadsDownloaded = 87,
    imagesDownloaded = 34,
    imagesTotal = 152,
    isRunning = true,
)

fun Fixtures.OfflineDownload.finished() = running().copy(
    imagesDownloaded = 152,
    isRunning = false,
)
