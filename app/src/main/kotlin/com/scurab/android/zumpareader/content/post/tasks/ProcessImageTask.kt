package com.scurab.android.zumpareader.content.post.tasks

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import android.os.AsyncTask
import java.io.File
import java.io.FileOutputStream

/**
 * Created by JBruchanov on 12/01/2016.
 */
public abstract class ProcessImageTask(private val src: String, private val output: String, private val  inSampleSize: Int, private val rotation: Int) : AsyncTask<Void, Void, String>() {

    public var imageResolution: Point? = null
    public var imageSize: Long = 0
    public var exception: Throwable? = null
        private set

    override fun doInBackground(vararg params: Void?): String? {
        try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = this@ProcessImageTask.inSampleSize
            }
            var bitmap = BitmapFactory.decodeFile(src, opts)
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true);
                if (bitmap != rotatedBitmap) {
                    bitmap.recycle()
                }
                bitmap = rotatedBitmap
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, FileOutputStream(output))
            imageResolution = Point(bitmap.width, bitmap.height)
            imageSize = File(output).length()
        } catch(e: Throwable) {
            exception = e
            e.printStackTrace()
            return null
        }
        return output
    }

    override abstract fun onPostExecute(result: String?)
}