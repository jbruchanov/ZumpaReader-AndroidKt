package com.scurab.android.zumpareader.usecase

import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.data.offlineImageUrls
import com.scurab.android.zumpareader.data.toDto
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.repository.ImagePrefetcher
import com.scurab.android.zumpareader.util.retrying
import kotlinx.coroutines.CancellationException
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
    data class Threads(val done: Int, val total: Int) : OfflineProgress
    data class Images(val done: Int, val total: Int) : OfflineProgress

    /**
     * @param data every thread that came back. Fewer than the total means some thread pages failed
     * and were skipped rather than throwing the rest away.
     * @param snapshotWritten false if the download is only in memory, so the caller can say so.
     */
    data class Done(
        val data: LinkedHashMap<String, ZumpaThread>,
        val snapshotWritten: Boolean,
    ) : OfflineProgress
}

/**
 * Builds the offline snapshot out of the forum itself.
 *
 * It used to be one POST to `zumpaws.scurab.com:8104/zumpa`, a web service that assembled the whole
 * thing server side and answered with json. **That service is gone**, so this walks the same pages
 * the app already reads and parses: [pages] of the thread list, then one thread page per thread for
 * the message bodies. Same `ZumpaAPI`, same parser, same models - the only thing that changed is
 * where the data comes from.
 *
 * That is N+1 requests where there used to be one, so the thread count is reported as it goes rather
 * than in one lump, and every step checks for cancellation. Sequential on purpose: this is a legacy
 * forum, and a snapshot the user asked for and can watch is worth more than one that hammers it.
 *
 * The image cache is behind [ImagePrefetcher] - Coil and its `Context` are an app concern, and
 * keeping them out is what lets the download itself be shared code.
 *
 * [api] must be the *online* api. Resolving the offline/online switch here would mean a download
 * started in offline mode read the snapshot it is supposed to be replacing.
 */
class OfflineDownloadUseCase(
    private val api: ZumpaAPI,
    private val images: ImagePrefetcher,
    private val json: Json,
) {

    fun run(
        pages: Int,
        downloadImages: Boolean,
        outJsonFile: String?,
        filter: String = DEFAULT_FILTER,
    ): Flow<OfflineProgress> = flow {
        val listed = loadThreadList(pages, filter)
        emit(OfflineProgress.Threads(done = 0, total = listed.size))

        val result = LinkedHashMap<String, ZumpaThread>()
        for (thread in listed) {
            currentCoroutineContext().ensureActive()
            //a thread that will not load is one missing thread, not a failed download - with one
            //request per thread there are too many ways for a single one to go wrong
            val items = try {
                retrying { api.getThreadPage(thread.id, thread.id) }.items
            } catch (err: CancellationException) {
                throw err
            } catch (_: Throwable) {
                continue
            }
            thread.offlineItems = items
            result[thread.id] = thread
            emit(OfflineProgress.Threads(done = result.size, total = listed.size))
        }

        //likewise: failing to write the file must not throw away what was just downloaded, so the
        //snapshot is best effort and the caller is told whether it landed
        val written = outJsonFile == null || runCatching {
            val dtos = result.mapValues { (_, thread) -> thread.toDto() }
            SystemFileSystem.sink(Path(outJsonFile)).buffered().use { it.writeString(json.encodeToString(dtos)) }
        }.isSuccess

        val urls = if (downloadImages) result.values.toList().offlineImageUrls() else emptySet()
        emit(OfflineProgress.Images(done = 0, total = urls.size))

        var downloaded = 0
        for (url in urls) {
            currentCoroutineContext().ensureActive()
            images.prefetch(url)
            downloaded++
            emit(OfflineProgress.Images(done = downloaded, total = urls.size))
        }

        emit(OfflineProgress.Done(result, snapshotWritten = written))
    }.flowOn(Dispatchers.Default)

    /**
     * Pages the list the same way the list screen does - follow `nextThreadId` until it runs out,
     * which is also how a shorter forum than [pages] terminates early.
     */
    private suspend fun loadThreadList(pages: Int, filter: String): List<ZumpaThread> {
        val out = LinkedHashMap<String, ZumpaThread>()
        var fromThread: String? = null
        repeat(pages) {
            currentCoroutineContext().ensureActive()
            val from = fromThread
            val page = retrying {
                if (from == null) api.getMainPage(filter) else api.getMainPage(from, filter)
            }
            out.putAll(page.items)
            if (page.nextThreadId.isEmpty()) {
                return out.values.toList()
            }
            fromThread = page.nextThreadId
        }
        return out.values.toList()
    }

    private companion object {
        /** `af=0`, the unfiltered list - what [com.scurab.android.zumpareader.util.ZumpaPrefs] falls back to. */
        const val DEFAULT_FILTER = "0"
    }
}
