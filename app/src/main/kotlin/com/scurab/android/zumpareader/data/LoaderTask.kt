package com.scurab.android.zumpareader.data

import android.net.Uri
import android.os.AsyncTask
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaWSBody
import com.scurab.android.zumpareader.util.isImageUri
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.*

/**
 * Created by JBruchanov on 15/01/2016.
 */

abstract class LoaderTask(private val zumpaApp: ZumpaReaderApp, val pages: Int, val images: Boolean, val outJsonFile: String?) : AsyncTask<Void, Void, LinkedHashMap<String, ZumpaThread>>() {

    public var imagesDownloading = 0
        private set
    public var imagesDownloaded = 0
        private set
    public var threadsDownloaded = 0
        private set

    override fun doInBackground(vararg params: Void?): LinkedHashMap<String, ZumpaThread>? {
        var result = LinkedHashMap<String, ZumpaThread>()
        try {
            val body = zumpaApp.zumpaWebServiceAPI.getZumpa(ZumpaWSBody(pages)).execute().body()
            if (body != null) {
                val parser = JsonParser();
                val items = parser.parse(InputStreamReader(ByteArrayInputStream(body.data)))
                        .asJsonObject.get("Context").asJsonObject
                        .get("Items").asJsonArray
                for (item in items) {
                    val thread = item.asJsonObject.asZumpaThread()
                    result.put(thread.id, thread)
                }

                if (outJsonFile != null) {
                    with(FileOutputStream(outJsonFile)) {
                        write(Gson().toJson(body).toByteArray())
                        close()
                    }
                }

                val urls = HashSet<String>()
                result.forEach {
                    it.value.offlineItems?.forEach {
                        it.urls?.forEach {
                            if (it.isImageUri()) {
                                urls.add(it)
                            }
                        }
                    }
                }
                threadsDownloaded = result.size
                imagesDownloading = urls.size
                notifyProgressChanged()

                if (urls.size > 0) {
                    val downloader = PicassoHttpDownloader.createDefault(zumpaApp, zumpaApp.zumpaHttpClient)
                    for (url in urls) {
                        try {
                            val uri = Uri.parse(url)
                            downloader.load(uri, 0, true)
                        } catch(e: Exception) {
                            e.printStackTrace()
                        }
                        imagesDownloaded++
                        notifyProgressChanged()
                        if (isCancelled) {
                            break;
                        }
                    }
                }

            }
        } catch(e: Throwable) {
            e.printStackTrace()
            return null
        }
        return result
    }

    abstract fun notifyProgressChanged()

    abstract override fun onPostExecute(result: LinkedHashMap<String, ZumpaThread>?)

    fun JsonObject.asZumpaThread() : ZumpaThread {
        return ZumpaThread(get("ID").asString, get("Subject").asString).apply {
            time = get("Time").asLong
            author = get("Author").asString
            hasResponseForYou = get("HasRespondForYou").asBoolean
            offlineItems = get("Items").asJsonArray.asZumpaThreadItems()
        }
    }

    fun JsonArray.asZumpaThreadItems() : List<ZumpaThreadItem> {
        val result = ArrayList<ZumpaThreadItem>()
        for (item in this) {
            val obj = item.asJsonObject
            val authorReal = obj.get("AuthorReal").asString
            val authorFake = if (obj.has("AuthorFake")) obj.get("AuthorReal").asString else authorReal
            val body = obj.get("Body").asString
            val time = obj.get("Time").asLong
            result.add(ZumpaThreadItem(authorFake, body, time).apply {
                this.authorReal = authorReal
                urls = if(obj.has("InsideUris")) obj.get("InsideUris").asJsonArray.asStrings() else null
            })
        }
        return result
    }

    fun JsonArray.asStrings(): List<String> {
        return map { v -> v.asString }
    }
}