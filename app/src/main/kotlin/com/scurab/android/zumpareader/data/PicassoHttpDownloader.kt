package com.scurab.android.zumpareader.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.ParseUtils
import com.squareup.okhttp.OkHttpClient
import com.squareup.picasso.Downloader
import com.squareup.picasso.OkHttpDownloader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Created by JBruchanov on 30/12/2015.
 */
public class PicassoHttpDownloader(private val imageStorage: File, private val displaySize: Point, client: OkHttpClient) : OkHttpDownloader(client) {
    private val htmlStart = '<'.toByte()
    private val maxHtmlCheck = 10 * 1024

    override fun load(uri: Uri?, networkPolicy: Int): Downloader.Response? {
        //        if(!uri.toString().contains("134560")){
        //            return null
        //        }

        var resultBitmap: Bitmap? = null
        var md5Uri = ParseUtils.MD5(uri.toString())
        if (md5Uri == null) {
            return super.load(uri, networkPolicy)
        }

        var result = tryLoadImage(md5Uri)
        if (result != null) {
            resultBitmap = result.second
            if (result.first && resultBitmap == null) {
                //loaded but it's not image
                return null
            }
        }

        if (resultBitmap == null) {
            var touchFile = true

            var response = super.load(uri, networkPolicy)
            var mem = ByteArrayOutputStream(Math.max(32 * 1024, response.contentLength.toInt()))
            response.inputStream.copyTo(mem)
            var byteArray = mem.toByteArray()

            if (byteArray.size > 0) {
                if (byteArray[0] == htmlStart) {
                    if (!uri!!.path.endsWith(".gif")) {//ignore gifs for now
                        //we have here potentially HTML
                        if (byteArray.size <= maxHtmlCheck) {
                            var content = String(byteArray)
                            var innerUrl = ZumpaSimpleParser.tryParseImage(content)
                            if (innerUrl != null) {
                                mem.reset()//clear current memory
                                response = super.load(Uri.parse(innerUrl), networkPolicy)
                                response.inputStream.copyTo(mem)
                                byteArray = mem.toByteArray()
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

            if (touchFile) {//to have empty file to avoid another loading
                File(imageStorage.absolutePath + "/" + md5Uri).createNewFile();
            }
        }
        if (resultBitmap == null) {
            return null
        }
        return Downloader.Response(resultBitmap, false)
    }


    private fun tryLoadImage(md5: String): Pair<Boolean, Bitmap?>? {
        var bitmap: Bitmap? = null
        var exists : Boolean
        var file = File(imageStorage.absolutePath + "/" + md5)
        exists = file.exists() && file.isFile
        var isImage = exists && file.length() > 0
        if (exists && isImage) {
            bitmap = BitmapFactory.decodeFile(file.absolutePath)
        }
        return Pair(exists, bitmap)
    }

    private fun saveImage(image: Bitmap, md5: String) {
        try {
            var file = File(imageStorage.absolutePath + "/" + md5)
            image.compress(Bitmap.CompressFormat.JPEG, 85, FileOutputStream(file));
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }
}