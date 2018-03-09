package com.scurab.android.zumpareader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.Environment
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.ParseUtils
import com.scurab.android.zumpareader.util.ZumpaPrefs
import com.squareup.picasso.Downloader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Created by JBruchanov on 24/11/2016.
 */
class PicassoHttpDownloader2(private val imageStorage: File,
                             private val displaySize: Point,
                             private val zumpaPrefs: ZumpaPrefs? = null,
                             private val httpClient: OkHttpClient) : Downloader {

    companion object {
        fun createDefault(context: Context, client: OkHttpClient, zumpaPrefs: ZumpaPrefs? = null): Downloader {
            return PicassoHttpDownloader2(getPicturesDir(context),
                    Point(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels),
                    zumpaPrefs,
                    client)
        }

        fun getPicturesDir(context: Context): File {
            var externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (externalFilesDir == null) {
                externalFilesDir = context.getDir(Environment.DIRECTORY_PICTURES, Context.MODE_PRIVATE)
            }
            return externalFilesDir
        }
    }

    private val htmlStart = '<'.toByte()
    private val maxHtmlCheck = 10 * 1024
    private val EMPTY_BITMAP = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)

    override fun shutdown() {

    }

    override fun load(uri: Uri, networkPolicy: Int): Downloader.Response? {
        return load(uri, networkPolicy, false)
    }

    private fun load(uri: Uri, networkPolicy: Int, justDownloading: Boolean): Downloader.Response? {
        var isOffline = zumpaPrefs?.isOffline ?: false
        var resultBitmap: Bitmap? = null
        var md5Uri = ParseUtils.MD5(uri.toString())
        if (md5Uri == null) {
            return null
        }

        var result = tryLoadImage(md5Uri, justDownloading)
        if (result != null) {
            resultBitmap = result.second
            if (result.first && resultBitmap == null) {
                //loaded but it's not image
                return null
            }
        }

        if (resultBitmap == null) {
            if (isOffline) {
                return null
            }
            var touchFile = true

            var byteArray = download(uri.toString())
            if (byteArray.isNotEmpty()) {
                if (byteArray[0] == htmlStart) {
                    if (!uri.path.endsWith(".gif")) {
                        //ignore gifs for now
                        //we have here potentially HTML
                        if (byteArray.size <= maxHtmlCheck) {
                            var content = String(byteArray)
                            var innerUrl = ZumpaSimpleParser.tryParseImage(content)
                            if (innerUrl != null) {
                                byteArray = download(innerUrl)
                                resultBitmap = ParseUtils.resizeImageIfNecessary(byteArray, displaySize)
                                if (resultBitmap != null) {
                                    saveImage(resultBitmap, md5Uri)
                                    touchFile = false
                                }
                            }
                        }
                    }
                } else {
                    resultBitmap = ParseUtils.resizeImageIfNecessary(byteArray, displaySize)
                    if (resultBitmap != null) {
                        saveImage(resultBitmap, md5Uri)
                        touchFile = false
                    }
                }
            }

            if (touchFile) {
                //to have empty file to avoid another loading
                File(imageStorage.absolutePath + "/" + md5Uri).createNewFile()
            }
        }
        if (resultBitmap == null) {
            return null
        }
        if (justDownloading) {
            resultBitmap.recycle()//won't be used later
        }
        return Downloader.Response(resultBitmap, false)
    }

    private fun tryLoadImage(md5: String, justDownloading: Boolean): Pair<Boolean, Bitmap?>? {
        var bitmap: Bitmap? = null
        var exists: Boolean
        var file = File(imageStorage.absolutePath + "/" + md5)
        exists = file.exists() && file.isFile
        var isImage = exists && file.length() > 0
        if (exists && isImage) {
            if (justDownloading) {
                bitmap = EMPTY_BITMAP//let's assume whatever is stored it's proper image
            } else {
                bitmap = BitmapFactory.decodeFile(file.absolutePath)
            }
        }
        return Pair(exists, bitmap)
    }

    private fun saveImage(image: Bitmap, md5: String) {
        try {
            var file = File(imageStorage.absolutePath + "/" + md5)
            image.compress(Bitmap.CompressFormat.JPEG, 85, FileOutputStream(file))
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    private fun download(uri: String): ByteArray {
        val request = Request.Builder().url(uri).build()
        var response = httpClient.newCall(request).execute()
        return response.body()!!.bytes()
    }
}