package com.scurab.android.zumpareader.usecase

import com.scurab.android.zumpareader.ZumpaWSAPI
import com.scurab.android.zumpareader.data.ZumpaWsResponseDto
import com.scurab.android.zumpareader.data.offlineImageUrls
import com.scurab.android.zumpareader.data.toDomain
import com.scurab.android.zumpareader.data.toDto
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaWSBody
import com.scurab.android.zumpareader.repository.ImagePrefetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.serialization.json.Json

sealed interface OfflineProgress {
    data class Threads(val count: Int) : OfflineProgress
    data class Images(val done: Int, val total: Int) : OfflineProgress
    data class Done(val data: LinkedHashMap<String, ZumpaThread>) : OfflineProgress
}

/**
 * Replaces `LoaderTask`, the last AsyncTask in the app.
 *
 * Unlike `AsyncTask.cancel(true)` this actually stops: cancelling the collecting scope breaks the
 * image prefetch loop at the next [ensureActive], where the old implementation kept downloading
 * every remaining image after the dialog was gone.
 *
 * The image cache is behind [ImagePrefetcher] - Coil and its `Context` are an app concern, and
 * keeping them out is what lets the download itself be shared code.
 */
class OfflineDownloadUseCase(
    private val ws: ZumpaWSAPI,
    private val images: ImagePrefetcher,
    private val json: Json,
) {

    fun run(pages: Int, downloadImages: Boolean, outJsonFile: String?): Flow<OfflineProgress> = flow {
        val result = LinkedHashMap<String, ZumpaThread>()
        val body = ws.getZumpa(ZumpaWSBody(pages))

        val response = json.decodeFromString<ZumpaWsResponseDto>(body.asUTFString())
        for (dto in response.context.items) {
            val thread = dto.toDomain()
            result[thread.id] = thread
        }

        if (outJsonFile != null) {
            val dtos = result.mapValues { (_, thread) -> thread.toDto() }
            SystemFileSystem.sink(Path(outJsonFile)).buffered().use { it.writeString(json.encodeToString(dtos)) }
        }

        val urls = if (downloadImages) result.values.toList().offlineImageUrls() else emptySet()

        emit(OfflineProgress.Threads(result.size))
        emit(OfflineProgress.Images(done = 0, total = urls.size))

        var downloaded = 0
        for (url in urls) {
            currentCoroutineContext().ensureActive()
            images.prefetch(url)
            downloaded++
            emit(OfflineProgress.Images(done = downloaded, total = urls.size))
        }

        emit(OfflineProgress.Done(result))
    }.flowOn(Dispatchers.Default)
}
