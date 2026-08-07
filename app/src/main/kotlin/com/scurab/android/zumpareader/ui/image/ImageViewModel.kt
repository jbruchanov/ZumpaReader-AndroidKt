package com.scurab.android.zumpareader.ui.image

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.repository.ImageCacheRepository
import kotlinx.coroutines.launch

sealed interface ImageUiState {
    data object Loading : ImageUiState
    data class Loaded(val bitmap: Bitmap) : ImageUiState

    /** Nothing decodable came back - the viewer hands the url to a browser and closes. */
    data class Failed(val url: String) : ImageUiState
}

class ImageViewModel(
    private val images: ImageCacheRepository,
) : BaseViewModel<ImageUiState>(ImageUiState.Loading) {

    private var isStarted = false

    fun start(url: String) {
        if (isStarted) return
        isStarted = true
        viewModelScope.launch {
            val bitmap = images.load(url)
            setState { if (bitmap != null) ImageUiState.Loaded(bitmap) else ImageUiState.Failed(url) }
        }
    }
}
