package com.scurab.android.zumpareader.content

import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.repository.AppEvent
import com.scurab.android.zumpareader.repository.AppEventBus
import com.scurab.android.zumpareader.repository.OfflineDataRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.usecase.OfflineDownloadUseCase
import com.scurab.android.zumpareader.usecase.OfflineProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class OfflineDownloadUiState(
    val pages: String = "1",
    val downloadImages: Boolean = false,
    val threadsDownloaded: Int = 0,
    val imagesDownloaded: Int = 0,
    val imagesTotal: Int = 0,
    val isRunning: Boolean = false,
) {
    /** The dialog swallows the back key while it works, as it always did. */
    val isDismissable: Boolean get() = !isRunning
}

sealed interface OfflineDownloadEffect : UiEffect {
    data object Dismiss : OfflineDownloadEffect
}

class OfflineDownloadViewModel(
    private val downloader: OfflineDownloadUseCase,
    private val offlineData: OfflineDataRepository,
    private val threads: ZumpaThreadRepository,
    private val eventBus: AppEventBus,
) : BaseViewModel<OfflineDownloadUiState>(OfflineDownloadUiState()) {

    private var job: Job? = null

    fun onPagesChanged(pages: String) {
        if (pages != state.pages) setState { copy(pages = pages) }
    }

    fun onDownloadImagesChanged(enabled: Boolean) {
        if (enabled != state.downloadImages) setState { copy(downloadImages = enabled) }
    }

    fun onStartClick() {
        if (state.isRunning) return
        val pages = state.pages.toIntOrNull() ?: return
        setState {
            copy(isRunning = true, threadsDownloaded = 0, imagesDownloaded = 0, imagesTotal = 0)
        }
        job = viewModelScope.launch {
            try {
                downloader.run(pages, state.downloadImages, offlineData.file.absolutePath)
                    .collect { progress ->
                        when (progress) {
                            is OfflineProgress.Threads ->
                                setState { copy(threadsDownloaded = progress.count) }

                            is OfflineProgress.Images -> setState {
                                copy(imagesDownloaded = progress.done, imagesTotal = progress.total)
                            }

                            is OfflineProgress.Done -> {
                                offlineData.setData(progress.data)
                                threads.replaceAll(progress.data)
                                eventBus.emit(AppEvent.OfflineDataChanged)
                            }
                        }
                    }
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isRunning = false) }
            }
        }
    }

    /** Stop while it runs, dismiss when it does not - the same one button as before. */
    fun onStopClick() {
        if (state.isRunning) {
            job?.cancel()
            job = null
            setState { copy(isRunning = false) }
        } else {
            effect(OfflineDownloadEffect.Dismiss)
        }
    }
}
