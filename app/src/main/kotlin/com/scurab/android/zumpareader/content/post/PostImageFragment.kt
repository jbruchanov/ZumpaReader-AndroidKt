package com.scurab.android.zumpareader.content.post

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.DialogFragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.pawegio.kandroid.find
import com.pawegio.kandroid.toast
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.content.SendingFragment
import com.scurab.android.zumpareader.drawable.SimpleProgressDrawable
import com.scurab.android.zumpareader.util.ParseUtils
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.getRandomCameraFileUri
import com.scurab.android.zumpareader.util.toast
import com.scurab.android.zumpareader.utils.FotoDiskProvider
import com.scurab.android.zumpareader.widget.PostImagePanelView
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileOutputStream

/**
 * Created by JBruchanov on 08/01/2016.
 */
public class PostImageFragment : DialogFragment(), SendingFragment {


    companion object {
        public fun newInstance(uri: Uri): PostImageFragment {
            return PostImageFragment().apply {
                arguments = arguments(uri)
            }
        }

        public fun arguments(uri: Uri): Bundle {
            return Bundle().apply {
                putParcelable(Intent.EXTRA_STREAM, uri)
            }
        }
    }

    override var sendingDialog: ProgressDialog? = null

    private val image: ImageView get() {
        return view!!.find<ImageView>(R.id.image)
    }

    private val imagePanel: PostImagePanelView get() {
        return view!!.find<PostImagePanelView>(R.id.post_image_panel_view)
    }

    private val imageUri by lazy { arguments.getParcelable<Uri>(Intent.EXTRA_STREAM) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_post_image, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            object : CopyTask(context, imageUri) {
                override fun onPostExecute(result: String?) {
                    if (context != null) {
                        Picasso.with(context).load(thumbnail).placeholder(SimpleProgressDrawable(context)).into(image)
                    }
                }
            }.start()
        } catch (e: Throwable) {
            context.toast(e.message)
        }
        imagePanel.upload.setOnClickListener { dispatchUpload() }
    }

    private fun dispatchUpload() {
        return//TODO:
        (image.drawable as? BitmapDrawable).exec {
            var file = context.getRandomCameraFileUri(false)
            if (it.bitmap.compress(Bitmap.CompressFormat.JPEG, 80, FileOutputStream(file))) {
                isSending = true
                object : UploadTask(file) {
                    override fun onPostExecute(result: String?) {
                        if (isResumed) {
                            isSending = false
                            context.toast(result)
                            Log.d("URL", result)
                        }
                    }
                }.execute()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isSending = false
    }
}

private abstract class CopyTask(private val context: Context, val uri: Uri) : AsyncTask<Void, Void, String>() {

    public var imageSize: Point? = null
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
        this.thumbnail = File(imageStorage, hash + "_thumbnail")
        this.output = output
        if (!(output.exists() && output.length() > 0L)) {
            super.execute()
        } else {
            onPostExecute(this.thumbnail?.absolutePath)
        }
    }

    private fun loadImageData(file: File) {
        try {
            val bitmapOptions = BitmapFactory.Options()
            bitmapOptions.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
            imageSize = Point(bitmapOptions.outWidth, bitmapOptions.outHeight)
            imageMime = bitmapOptions.outMimeType
        } catch(t: Throwable) {
            t.printStackTrace()
        }
    }

    private val MAX_IMAGE_SIZE = 1000
    private fun createThumbnail(src: File, to: File) {
        try {
            if (imageSize?.x ?: 0 > MAX_IMAGE_SIZE || imageSize?.y ?: 0 > MAX_IMAGE_SIZE) {
                val bitmapOptions = BitmapFactory.Options()
                var scale = 1
                var size = Math.max(imageSize!!.x, imageSize!!.y)
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

private abstract class UploadTask(val file: String) : AsyncTask<Void, Void, String>() {

    override fun doInBackground(vararg params: Void?): String? {
        try {
            return FotoDiskProvider.uploadPicture(file, null)
        } catch(t: Throwable) {
            return null
        }
    }

    override abstract fun onPostExecute(result: String?)
}