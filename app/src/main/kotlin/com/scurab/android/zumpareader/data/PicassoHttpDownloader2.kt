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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Created by JBruchanov on 24/11/2016.
 */
class PicassoHttpDownloader2(
    private val imageStorage: File,
    private val displaySize: Point,
    private val zumpaPrefs: ZumpaPrefs? = null,
    private val httpClient: OkHttpClient
) : Downloader {

    companion object {
        fun createDefault(context: Context, client: OkHttpClient, zumpaPrefs: ZumpaPrefs? = null): Downloader {
            return PicassoHttpDownloader2(
                getPicturesDir(context),
                Point(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels),
                zumpaPrefs,
                client
            )
        }

        fun getPicturesDir(context: Context): File {
            var externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (externalFilesDir == null) {
                externalFilesDir = context.getDir(Environment.DIRECTORY_PICTURES, Context.MODE_PRIVATE)
            }
            return externalFilesDir!!
        }
    }

    private val htmlStart = '<'.toByte()
    private val maxHtmlCheck = 10 * 1024

    override fun load(request: Request): Response {
        return load(Uri.parse(request.url.toString()), false)
            ?: throw IOException("Unable to load request:${request}, internal factory returned null")
    }

    override fun shutdown() {
        //nothing to do here
    }

    private fun load(uri: Uri, justDownloading: Boolean): Response? {
        val isOffline = zumpaPrefs?.isOffline ?: false
        var result: ByteArray? = null
        val md5Uri = ParseUtils.MD5(uri.toString()) ?: return null

        val (exists, filePath) = tryLoadImage(md5Uri, justDownloading)
        if (exists && filePath == null) {
            //loaded but it's not image
            return null
        }


        if (isOffline) {
            return null
        }
        var touchFile = true
        result = download(uri.toString())
        if (result.isNotEmpty()) {
            if (result[0] == htmlStart) {
                val endsWithGif = uri.path?.endsWith(".gif") == true
                if (!endsWithGif) {
                    //ignore gifs for now
                    //we have here potentially HTML
                    if (result.size <= maxHtmlCheck) {
                        val content = String(result)
                        val innerUrl = ZumpaSimpleParser.tryParseImage(content)
                        if (innerUrl != null) {
                            result = download(innerUrl)
                            val resultBitmap = ParseUtils.resizeImageIfNecessary(result, displaySize)
                            if (resultBitmap != null) {
                                saveImage(resultBitmap, md5Uri)
                                touchFile = false
                            }
                        }
                    }
                }
            } else {
                val resultBitmap = ParseUtils.resizeImageIfNecessary(result, displaySize)
                if (resultBitmap != null) {
                    saveImage(resultBitmap, md5Uri)
                    resultBitmap.recycle()
                    touchFile = false
                }
            }
        }

        if (touchFile) {
            //to have empty file to avoid another loading
            File(imageStorage.absolutePath + "/" + md5Uri).createNewFile()
        }
        if (result == null) {
            return null
        }
        return Response.Builder()
            .body(result.toResponseBody())
            .build()
    }

    private fun tryLoadImage(md5: String, justDownloading: Boolean): Pair<Boolean, String?> {
        var bitmapPath: String? = null
        val exists: Boolean
        val file = File(imageStorage.absolutePath + "/" + md5)
        exists = file.exists() && file.isFile
        val isImage = exists && file.length() > 0
        if (exists && isImage) {
            if (justDownloading) {
                bitmapPath = null
            } else {
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                if (opts.outWidth != 0 && opts.outHeight != 0) {
                    bitmapPath = file.absolutePath
                }
            }
        }
        return Pair(exists, bitmapPath)
    }

    private fun saveImage(image: Bitmap, md5: String) {
        try {
            var file = File(imageStorage.absolutePath + "/" + md5)
            image.compress(Bitmap.CompressFormat.JPEG, 85, FileOutputStream(file))
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun download(uri: String): ByteArray {
        val request = Request.Builder().url(uri).build()
        var response = httpClient.newCall(request).execute()
        return response.body!!.bytes()
    }
}
