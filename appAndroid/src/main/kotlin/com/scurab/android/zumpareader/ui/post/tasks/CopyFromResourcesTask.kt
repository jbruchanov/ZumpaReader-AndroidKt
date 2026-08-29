package com.scurab.android.zumpareader.ui.post.tasks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.Environment
import com.scurab.android.zumpareader.util.ParseUtils
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by JBruchanov on 12/01/2016.
 */
class CopyFromResourcesTask(private val context: Context, val uri: Uri) {

    suspend fun execute(): CopyFromResourcesTaskResult = withContext(Dispatchers.IO) {
        val result = CopyFromResourcesTaskResult()
        val imageStorage = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        //locals, where these were two fields nothing outside this function ever read. As a
        //nullable field the hash went straight into `File(parent, child)`, a java constructor that
        //does not accept null - so a device with no MD5 would have thrown there, and the thumbnail
        //beside it would have been named "null_thumbnail".
        val hash = requireNotNull(ParseUtils.MD5(uri.toString())) { "no MD5 digest on this device" }
        val output = File(imageStorage, hash)
        val thumb = File(imageStorage, hash + "_thumbnail")
        result.thumbnail = thumb
        result.imageFile = output
        if (!(output.exists() && output.length() > 0L)) {
            if (!(output.exists() && output.length() > 0L)) {
                val stream = requireNotNull(context.contentResolver.openInputStream(uri))
                stream.copyTo(FileOutputStream(output))
                loadImageData(output, result)
                createThumbnail(output, result.thumbnail!!, result)
            }
        } else {
            loadImageData(output, result)
            if (!thumb.exists() || thumb.length() == 0L) {
                createThumbnail(output, result.thumbnail!!, result)
            }
        }
        result
    }

    private fun loadImageData(file: File, result: CopyFromResourcesTaskResult) {
        try {
            val bitmapOptions = BitmapFactory.Options()
            bitmapOptions.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
            result.imageResolution = Point(bitmapOptions.outWidth, bitmapOptions.outHeight)
            result.imageSize = file.length()
            result.imageMime = bitmapOptions.outMimeType
        } catch(t: Throwable) {
            t.printStackTrace()
        }
    }

    private val MAX_IMAGE_SIZE = 1000
    private fun createThumbnail(src: File, to: File, result: CopyFromResourcesTaskResult) {
        try {
            if (result.imageResolution?.x ?: 0 > MAX_IMAGE_SIZE || result.imageResolution?.y ?: 0 > MAX_IMAGE_SIZE) {
                val bitmapOptions = BitmapFactory.Options()
                var scale = 1
                var size = Math.max(result.imageResolution!!.x, result.imageResolution!!.y)
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

class CopyFromResourcesTaskResult {
    var imageResolution: Point? = null
    var imageSize: Long = 0
    var imageMime: String? = null
    var imageFile: File? = null
    var thumbnail: File? = null
}
