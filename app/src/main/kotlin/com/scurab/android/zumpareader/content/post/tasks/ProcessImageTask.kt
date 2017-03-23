package com.scurab.android.zumpareader.content.post.tasks

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import io.reactivex.Single
import io.reactivex.SingleObserver
import java.io.File
import java.io.FileOutputStream

/**
 * Created by JBruchanov on 12/01/2016.
 */
class ProcessImageTask(private val src: String, private val output: String, private val inSampleSize: Int, private val rotation: Int) : Single<ProcessImageTaskResult>() {

    override fun subscribeActual(observer: SingleObserver<in ProcessImageTaskResult>) {
        val result = ProcessImageTaskResult()

        val opts = BitmapFactory.Options().apply {
            inSampleSize = this@ProcessImageTask.inSampleSize
        }
        var bitmap = BitmapFactory.decodeFile(src, opts)
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (bitmap != rotatedBitmap) {
                bitmap.recycle()
            }
            bitmap = rotatedBitmap
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, FileOutputStream(output))
        result.imageResolution = Point(bitmap.width, bitmap.height)
        result.imageSize = File(output).length()

        observer.onSuccess(result)
    }
}

class ProcessImageTaskResult {
    var imageResolution: Point? = null
    var imageSize: Long = 0
}