package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.ZumpaPHPAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** The fotodisk upload behind the same shape as the other repositories. */
class ImageUploadRepository(private val api: ZumpaPHPAPI) {

    /**
     * Uploads [preferred] when it exists on disk, otherwise [fallback] - the resized file is only
     * there once the user has actually resized or rotated.
     */
    suspend fun upload(preferred: String?, fallback: String): String = withContext(Dispatchers.IO) {
        val file = preferred?.let(::File)?.takeIf { it.exists() } ?: File(fallback)
        val part = MultipartBody.Part.createFormData(
            "image",
            file.name,
            file.asRequestBody("image/*".toMediaType())
        )
        val name = "Submit".toByteArray().toRequestBody("text/plain".toMediaType())
        api.postImage(part, name).asUTFString()
    }
}
