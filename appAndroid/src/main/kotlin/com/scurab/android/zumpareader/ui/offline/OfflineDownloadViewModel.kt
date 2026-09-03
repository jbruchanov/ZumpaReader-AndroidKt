package com.scurab.android.zumpareader.ui.offline

import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.ShowSnackbar
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
    val downloadImages: Boolean = true,
    val threadsDownloaded: Int = 0,
    val threadsTotal: Int = 0,
    val imagesDownloaded: Int = 0,
    val imagesTotal: Int = 0,
    val isRunning: Boolean = false,
) {
    /** The dialog swallows the back key while it works, as it always did. */
    val isDismissable: Boolean get() = !isRunning

    val canStart: Boolean get() = !isRunning && pages.toIntOrNull() != null
}

sealed interface OfflineDownloadEffect : UiEffect {
    data object Dismiss : OfflineDownloadEffect
}

interface OfflineDownloadEventHandler {
    fun onPagesChanged(pages: String)
    fun onDownloadImagesToggled(enabled: Boolean)
    fun onStartClicked()
    fun onStopClicked()
}

class OfflineDownloadViewModel(
    private val downloader: OfflineDownloadUseCase,
    private val offlineData: OfflineDataRepository,
    private val threads: ZumpaThreadRepository,
    private val eventBus: AppEventBus,
) : BaseViewModel<OfflineDownloadUiState>(OfflineDownloadUiState()), OfflineDownloadEventHandler {

    private var job: Job? = null

    override fun onPagesChanged(pages: String) {
        if (pages != state.pages) setState { copy(pages = pages) }
    }

    override fun onDownloadImagesToggled(enabled: Boolean) {
        if (enabled != state.downloadImages) setState { copy(downloadImages = enabled) }
    }

    override fun onStartClicked() {
        if (state.isRunning) return
        val pages = state.pages.toIntOrNull() ?: return
        setState {
            copy(
                isRunning = true,
                threadsDownloaded = 0,
                threadsTotal = 0,
                imagesDownloaded = 0,
                imagesTotal = 0,
            )
        }
        job = viewModelScope.launch {
            try {
                downloader.run(pages, state.downloadImages, offlineData.path)
                    .collect { progress ->
                        when (progress) {
                            is OfflineProgress.Threads -> setState {
                                copy(
                                    threadsDownloaded = progress.done,
                                    threadsTotal = progress.total,
                                )
                            }

                            is OfflineProgress.Images -> setState {
                                copy(imagesDownloaded = progress.done, imagesTotal = progress.total)
                            }

                            //an empty result is a failed download, not a new snapshot - it must
                            //not replace whatever is already there
                            is OfflineProgress.Done -> if (progress.data.isEmpty()) {
                                effect(ShowSnackbar(resId = R.string.err_fail))
                            } else {
                                offlineData.setData(progress.data)
                                threads.replaceAll(progress.data)
                                eventBus.emit(AppEvent.OfflineDataChanged)
                                if (!progress.snapshotWritten) {
                                    //in memory for this session, but nothing to load next time
                                    effect(ShowSnackbar(resId = R.string.err_fail))
                                }
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
    override fun onStopClicked() {
        if (state.isRunning) {
            job?.cancel()
            job = null
            setState { copy(isRunning = false) }
        } else {
            effect(OfflineDownloadEffect.Dismiss)
        }
    }
}
