package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.ZumpaPHPAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** The fotodisk upload behind the same shape as the other repositories. */
class ImageUploadRepository(private val api: ZumpaPHPAPI) {

    /**
     * Uploads [preferred] when it exists on disk, otherwise [fallback] - the resized file is only
     * there once the user has actually resized or rotated.
     */
    suspend fun upload(preferred: String?, fallback: String): String = withContext(Dispatchers.IO) {
        val path = preferred?.let(::Path)?.takeIf { SystemFileSystem.exists(it) } ?: Path(fallback)
        //read rather than streamed: this is an already-resized photo, and it keeps the multipart
        //body free of any file handling in the api layer
        val bytes = SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        api.postImage(path.name, bytes).asUTFString()
    }
}
