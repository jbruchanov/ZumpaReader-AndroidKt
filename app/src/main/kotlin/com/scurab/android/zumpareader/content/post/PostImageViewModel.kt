package com.scurab.android.zumpareader.content.post

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.BaseViewModel
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.content.post.tasks.CopyFromResourcesTask
import com.scurab.android.zumpareader.content.post.tasks.ProcessImageTask
import com.scurab.android.zumpareader.repository.ImageUploadRepository
import kotlinx.coroutines.launch

data class ImageMetaUiState(val width: Int, val height: Int, val bytes: Long)

/**
 * The clearest ViewModel case in the app: the fragment used to keep seven fields alive across view
 * destruction behind a `restoreState` boolean flipped in onDestroyView/onDestroy.
 */
data class PostImageUiState(
    val thumbnailPath: String? = null,
    val original: ImageMetaUiState? = null,
    val resized: ImageMetaUiState? = null,
    val rotationDegrees: Int = 0,
    val sampleSizeIndex: Int = 0,
    val uploadedLink: String? = null,
    val isBusy: Boolean = false,
)

sealed interface PostImageEffect : UiEffect {
    data class ImageUploaded(val link: String) : PostImageEffect
}

class PostImageViewModel(
    private val context: Context,
    private val uploads: ImageUploadRepository,
) : BaseViewModel<PostImageUiState>(PostImageUiState()) {

    private var sourceFile: String? = null
    private var isStarted = false

    private val outputFile: String? get() = sourceFile?.let { "${it}_out" }

    fun start(uri: Uri) {
        if (isStarted) return
        isStarted = true
        viewModelScope.launch {
            try {
                val result = CopyFromResourcesTask(context, uri).execute()
                sourceFile = result.imageFile?.absolutePath
                setState {
                    copy(
                        thumbnailPath = result.thumbnail?.absolutePath,
                        original = result.imageResolution?.let {
                            ImageMetaUiState(it.x, it.y, result.imageSize)
                        },
                    )
                }
            } catch (err: Throwable) {
                onError(err)
            }
        }
    }

    fun onSampleSizeSelected(index: Int) {
        if (index != state.sampleSizeIndex) {
            setState { copy(sampleSizeIndex = index) }
        }
    }

    fun onResizeClick() = process(state.rotationDegrees)

    fun onRotateClick() {
        val rotation = (state.rotationDegrees + ROTATION_STEP) % FULL_TURN
        setState { copy(rotationDegrees = rotation) }
        process(rotation)
    }

    private fun process(rotation: Int) {
        val source = sourceFile ?: return
        val output = outputFile ?: return
        val inSample = 1 shl state.sampleSizeIndex
        setState { copy(isBusy = true) }
        viewModelScope.launch {
            try {
                val result = ProcessImageTask(source, output, inSample, rotation).execute()
                setState {
                    copy(
                        resized = result.imageResolution?.let {
                            ImageMetaUiState(it.x, it.y, result.imageSize)
                        },
                    )
                }
            } catch (err: Throwable) {
                onError(err)
            } finally {
                setState { copy(isBusy = false) }
            }
        }
    }

    fun onUploadClick() {
        val source = sourceFile ?: return
        setState { copy(isBusy = true) }
        viewModelScope.launch {
            try {
                //the resized file if there is one, otherwise the original
                val link = uploads.upload(outputFile, source)
                if (link.isEmpty()) {
                    effect(ShowToast(resId = R.string.err_fail))
                } else {
                    setState { copy(uploadedLink = link) }
                    effect(PostImageEffect.ImageUploaded(link))
                }
            } catch (err: Throwable) {
                err.printStackTrace()
                effect(ShowToast(resId = R.string.err_fail))
            } finally {
                setState { copy(isBusy = false) }
            }
        }
    }

    private companion object {
        const val ROTATION_STEP = 90
        const val FULL_TURN = 360
    }
}
