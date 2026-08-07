package com.scurab.android.zumpareader.repository

import android.content.Context
import android.os.Environment
import com.github.salomonbrys.kotson.DeserializerArg
import com.github.salomonbrys.kotson.registerTypeAdapter
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.scurab.android.zumpareader.ZumpaOfflineApi
import com.scurab.android.zumpareader.gson.GsonExcludeStrategy
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.util.ZumpaPrefs
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * The offline snapshot on disk and the api that serves it. Lifted out of
 * [com.scurab.android.zumpareader.ZumpaReaderApp] unchanged.
 */
class OfflineDataRepository(
    private val context: Context,
    private val offlineApi: ZumpaOfflineApi,
    private val prefs: ZumpaPrefs,
) {

    val file: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), OFFLINE_FILE_NAME)

    /** Only reads when offline mode is on, as it always did. */
    fun loadFromDisk() {
        val offline = file
        if (!offline.exists() || !prefs.isOffline) {
            return
        }
        val gson = GsonBuilder()
            .setExclusionStrategies(GsonExcludeStrategy())
            .registerTypeAdapter<ZumpaThread> {
                deserialize { elem ->
                    if (elem is DeserializerArg) {
                        ZumpaThread.thread(elem.json as JsonObject)
                    } else {
                        ZumpaThread.thread(elem as JsonObject)
                    }
                }
            }
            .create()
        val type = object : TypeToken<LinkedHashMap<String, ZumpaThread>>() {}.type
        JsonReader(InputStreamReader(FileInputStream(offline))).use { reader ->
            offlineApi.offlineData = gson.fromJson(reader, type)
        }
    }

    fun setData(data: LinkedHashMap<String, ZumpaThread>) {
        offlineApi.offlineData = data
    }

    companion object {
        const val OFFLINE_FILE_NAME = "offline.json"
    }
}
