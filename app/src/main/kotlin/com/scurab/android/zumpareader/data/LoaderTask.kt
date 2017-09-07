package com.scurab.android.zumpareader.data

import android.os.AsyncTask
import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.string
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.gson.GsonExcludeStrategy
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaWSBody
import com.scurab.android.zumpareader.util.isImageUri
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.*
import com.facebook.datasource.DataSources
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.request.ImageRequest


/**
 * Created by JBruchanov on 15/01/2016.
 */

abstract class LoaderTask(private val zumpaApp: ZumpaReaderApp, val pages: Int, val downloadImages: Boolean, val outJsonFile: String?) : AsyncTask<Void, Void, LinkedHashMap<String, ZumpaThread>>() {

    var imagesDownloading = 0
        private set
    var imagesDownloaded = 0
        private set
    var threadsDownloaded = 0
        private set
    var exception: Throwable? = null
        private set

    override fun doInBackground(vararg params: Void?): LinkedHashMap<String, ZumpaThread>? {
        var result = LinkedHashMap<String, ZumpaThread>()
        try {
            val body = zumpaApp.zumpaWebServiceAPI.getZumpa(ZumpaWSBody(pages)).execute().body()
            if (body != null) {
                val parser = JsonParser()
                val items = parser.parse(InputStreamReader(ByteArrayInputStream(body.data)))
                        .asJsonObject.get("Context").asJsonObject
                        .get("Items").asJsonArray
                for (item in items) {
                    val thread = item.asJsonObject.asZumpaThread()
                    result.put(thread.id, thread)
                }

                if (outJsonFile != null) {
                    with(FileOutputStream(outJsonFile)) {
                        val gson = GsonBuilder().setExclusionStrategies(GsonExcludeStrategy()).create()
                        write(gson.toJson(result).toByteArray())
                        close()
                    }
                }

                val urls = HashSet<String>()
                if (downloadImages) {
                    result.forEach {
                        it.value.offlineItems?.forEach {
                            it.urls?.forEach {
                                if (it.isImageUri()) {
                                    urls.add(it)
                                }
                            }
                        }
                    }
                }
                threadsDownloaded = result.size
                imagesDownloading = urls.size
                notifyProgressChanged()

                if (urls.size > 0) {
                    urls.forEach {
                        val imagePipeline = Fresco.getImagePipeline()
                        val request = ImageRequest.fromUri(it)
                        val dataSource = imagePipeline.prefetchToDiskCache(request, null)
                        try {
                            DataSources.waitForFinalResult(dataSource)
                        } finally {
                            dataSource.close()
                        }
                        imagesDownloaded++
                        notifyProgressChanged()
                    }
                }

            }
        } catch(e: Throwable) {
            exception = e
            e.printStackTrace()
            return null
        }
        return result
    }

    abstract fun notifyProgressChanged()

    abstract override fun onPostExecute(result: LinkedHashMap<String, ZumpaThread>?)

    fun JsonObject.asZumpaThread(): ZumpaThread {
        return ZumpaThread(get("ID").string, get("Subject").string).apply {
            time = get("Time").long
            author = get("Author").string
            hasResponseForYou = get("HasRespondForYou").bool
            offlineItems = get("Items").asJsonArray.asZumpaThreadItems()
        }
    }

    fun JsonArray.asZumpaThreadItems(): List<ZumpaThreadItem> {
        val result = ArrayList<ZumpaThreadItem>()
        for (item in this) {
            val obj = item.asJsonObject
            val authorReal = obj.get("AuthorReal").string
            val authorFake = if (obj.has("AuthorFake")) obj.get("AuthorReal").string else authorReal
            val body = obj.get("Body").string
            val time = obj.get("Time").long
            result.add(ZumpaThreadItem(authorFake, body, time).apply {
                this.authorReal = authorReal
                urls = if (obj.has("InsideUris")) obj.get("InsideUris").asJsonArray.asStrings() else null
            })
        }
        return result
    }

    fun JsonArray.asStrings(): List<String> {
        return map { v -> v.asString }
    }
}