package com.scurab.android.zumpareader.ui.image

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.repository.ImageCacheRepository
import kotlinx.coroutines.launch

sealed interface ImageUiState {
    data object Loading : ImageUiState

    /**
     * A [Bitmap] rather than a url because the viewer reaches into the fresco pipeline by hand.
     * Becomes a Coil model in C9, at which point this stops being a heavy object in a ui state.
     */
    data class Loaded(val bitmap: Bitmap) : ImageUiState

    /** Nothing decodable came back - the viewer hands the url to a browser and closes. */
    data class Failed(val url: String) : ImageUiState
}

sealed interface ImageEffect : UiEffect {
    data class OpenInBrowser(val url: String) : ImageEffect
    data object Close : ImageEffect
}

interface ImageEventHandler {
    fun onCloseRequested()
}

class ImageViewModel(
    private val images: ImageCacheRepository,
) : BaseViewModel<ImageUiState>(ImageUiState.Loading), ImageEventHandler {

    private var isStarted = false

    fun start(url: String) {
        if (isStarted) return
        isStarted = true
        viewModelScope.launch {
            val bitmap = images.load(url)
            if (bitmap != null) {
                setState { ImageUiState.Loaded(bitmap) }
            } else {
                setState { ImageUiState.Failed(url) }
                effect(ImageEffect.OpenInBrowser(url))
            }
        }
    }

    override fun onCloseRequested() = effect(ImageEffect.Close)
}
