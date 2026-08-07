package com.scurab.android.zumpareader.usecase

import com.facebook.datasource.DataSources
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.request.ImageRequest
import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.string
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.scurab.android.zumpareader.ZumpaWSAPI
import com.scurab.android.zumpareader.gson.GsonExcludeStrategy
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaWSBody
import com.scurab.android.zumpareader.util.looksLikeImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

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
 */
class OfflineDownloadUseCase(private val ws: ZumpaWSAPI) {

    fun run(pages: Int, downloadImages: Boolean, outJsonFile: String?): Flow<OfflineProgress> = flow {
        val result = LinkedHashMap<String, ZumpaThread>()
        val body = ws.getZumpa(ZumpaWSBody(pages)).execute().body() ?: return@flow

        val items = JsonParser()
            .parse(InputStreamReader(ByteArrayInputStream(body.data)))
            .asJsonObject.get("Context").asJsonObject
            .get("Items").asJsonArray
        for (item in items) {
            val thread = item.asJsonObject.asZumpaThread()
            result[thread.id] = thread
        }

        if (outJsonFile != null) {
            FileOutputStream(outJsonFile).use { stream ->
                val gson = GsonBuilder().setExclusionStrategies(GsonExcludeStrategy()).create()
                stream.write(gson.toJson(result).toByteArray())
            }
        }

        val urls = LinkedHashSet<String>()
        if (downloadImages) {
            result.values.forEach { thread ->
                thread.offlineItems?.forEach { item ->
                    item.urls?.forEach { url ->
                        if (url.looksLikeImageUrl()) {
                            urls += url
                        }
                    }
                }
            }
        }

        emit(OfflineProgress.Threads(result.size))
        emit(OfflineProgress.Images(done = 0, total = urls.size))

        var downloaded = 0
        for (url in urls) {
            currentCoroutineContext().ensureActive()
            val dataSource = Fresco.getImagePipeline()
                .prefetchToDiskCache(ImageRequest.fromUri(url), null)
            try {
                DataSources.waitForFinalResult(dataSource)
            } finally {
                dataSource.close()
            }
            downloaded++
            emit(OfflineProgress.Images(done = downloaded, total = urls.size))
        }

        emit(OfflineProgress.Done(result))
    }.flowOn(Dispatchers.IO)
}

private fun JsonObject.asZumpaThread(): ZumpaThread {
    return ZumpaThread(get("ID").string, get("Subject").string).apply {
        time = get("Time").long
        author = get("Author").string
        hasResponseForYou = get("HasRespondForYou").bool
        offlineItems = get("Items").asJsonArray.asZumpaThreadItems()
    }
}

private fun JsonArray.asZumpaThreadItems(): List<ZumpaThreadItem> {
    return map { element ->
        val obj = element.asJsonObject
        val authorReal = obj.get("AuthorReal").string
        val authorFake = if (obj.has("AuthorFake")) obj.get("AuthorReal").string else authorReal
        ZumpaThreadItem(authorFake, obj.get("Body").string, obj.get("Time").long).apply {
            this.authorReal = authorReal
            urls = if (obj.has("InsideUris")) {
                obj.get("InsideUris").asJsonArray.map { it.asString }
            } else {
                null
            }
        }
    }
}
