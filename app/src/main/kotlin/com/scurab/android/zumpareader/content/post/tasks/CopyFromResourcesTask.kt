package com.scurab.android.zumpareader.content.post.tasks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import com.scurab.android.zumpareader.util.ParseUtils
import java.io.File
import java.io.FileOutputStream

/**
 * Created by JBruchanov on 12/01/2016.
 */
public abstract class CopyFromResourcesTask(private val context: Context, val uri: Uri) : AsyncTask<Void, Void, String>() {

    public var imageResolution: Point? = null
    public var imageSize: Long = 0
    public var imageMime: String? = null
    public var thumbnail: File? = null
    private var output: File? = null
    private var imageStorage: File? = null
    private var hash: String? = null

    override fun doInBackground(vararg params: Void?): String? {
        try {
            val output = this.output!!
            if (!(output.exists() && output.length() > 0L)) {
                var stream = context.contentResolver.openInputStream(uri)
                stream.copyTo(FileOutputStream(output))
                loadImageData(output)
                createThumbnail(output, thumbnail!!)
            }
            this.thumbnail = thumbnail
            return output.absolutePath
        } catch(e: Throwable) {
            return null
        }
    }

    public fun start() {
        imageStorage = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        hash = ParseUtils.MD5(uri.toString())
        val output = File(imageStorage, hash)
        val thumb = File(imageStorage, hash + "_thumbnail")
        this.thumbnail = thumb
        this.output = output
        if (!(output.exists() && output.length() > 0L)) {
            super.execute()
        } else {
            loadImageData(output)
            if (!thumb.exists() || thumb.length() == 0L) {
                createThumbnail(output, thumbnail!!)
            }
            onPostExecute(output.absolutePath)
        }
    }

    private fun loadImageData(file: File) {
        try {
            val bitmapOptions = BitmapFactory.Options()
            bitmapOptions.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
            imageResolution = Point(bitmapOptions.outWidth, bitmapOptions.outHeight)
            imageSize = file.length()
            imageMime = bitmapOptions.outMimeType
        } catch(t: Throwable) {
            t.printStackTrace()
        }
    }

    private val MAX_IMAGE_SIZE = 1000
    private fun createThumbnail(src: File, to: File) {
        try {
            if (imageResolution?.x ?: 0 > MAX_IMAGE_SIZE || imageResolution?.y ?: 0 > MAX_IMAGE_SIZE) {
                val bitmapOptions = BitmapFactory.Options()
                var scale = 1
                var size = Math.max(imageResolution!!.x, imageResolution!!.y)
                while ((size / scale > MAX_IMAGE_SIZE)) {
                    scale *= 2
                }
                bitmapOptions.inSampleSize = scale
                val bitmap = BitmapFactory.decodeFile(src.absolutePath, bitmapOptions)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, FileOutputStream(to))
                bitmap.recycle()
            } else {
                src.copyTo(to)
            }
        } catch(t: Throwable) {
            t.printStackTrace()
        }
    }
}