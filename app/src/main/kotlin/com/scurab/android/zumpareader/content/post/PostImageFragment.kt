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
import com.scurab.android.zumpareader.content.post.tasks.CopyFromResourcesTask
import com.scurab.android.zumpareader.content.post.tasks.ProcessImageTask
import com.scurab.android.zumpareader.content.post.tasks.UploadImageTask
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
    private var imageFile : String? = null
    private var imageRotation = 0
    private var restoreState = false

    private var imageResolution: Point? = null
    private var imageSize: Long = 0
    private var imageResizedResolution: Point? = null
    private var imageResizedSize: Long = 0

    private val imageFileToUpload: String? get() {
        return if (imageFile != null) imageFile + "_out" else null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_post_image, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            object : CopyFromResourcesTask(context, imageUri) {
                override fun onPostExecute(result: String?) {
                    if (context != null && result != null) {
                        imageFile = result
                        if (!restoreState) {
                            this@PostImageFragment.imageSize = imageSize
                            this@PostImageFragment.imageResolution = this.imageResolution
                            imagePanel.setImageSize(this.imageResolution, imageSize)
                        }
                        Picasso.with(context).load(thumbnail).placeholder(SimpleProgressDrawable(context)).into(image)

                    }
                }
            }.start()
            if (restoreState) {
                imagePanel.setImageSize(imageResolution, imageSize)
                if (imageResizedResolution != null) {
                    imagePanel.setResizedImageSize(imageResizedResolution!!, imageResizedSize)
                }
                image.rotation = imageRotation.toFloat()
            }
        } catch (e: Throwable) {
            context.toast(e.message)
        }
        imagePanel.upload.setOnClickListener { dispatchUpload() }
        imagePanel.resize.setOnClickListener { onImageResize() }
        imagePanel.rotateRight.setOnClickListener { onImageRotate() }
    }

    protected fun onImageResize() {
        val size = 1 shl imagePanel.sizeSpinner.selectedItemPosition
        onImageProcess(size, imageRotation)
    }

    protected fun onImageRotate() {
        imageRotation = (imageRotation + 90) % 360

        val size = 1 shl imagePanel.sizeSpinner.selectedItemPosition
        onImageProcess(size, imageRotation)
    }

    private fun onImageProcess(inSample: Int, imageRotation: Int) {
        isSending = true
        imageFile.exec {
            object : ProcessImageTask(it, imageFileToUpload!!, inSample, imageRotation){

                override fun onPostExecute(result: String?) {
                    isSending = false
                    context.exec {
                        if (result != null) {
                            image.animate().rotation(imageRotation.toFloat())
                            imageResizedResolution = this.imageResolution!!
                            imageResizedSize = this.imageSize
                            imagePanel.setResizedImageSize(this.imageResolution!!, this.imageSize)
                        }
                        if (exception != null) {
                            it.toast(exception!!.message)
                        }
                    }
                }
            }.execute()
        }
    }

    private fun dispatchUpload() {
        return//TODO:
        (image.drawable as? BitmapDrawable).exec {
            var file = context.getRandomCameraFileUri(false)
            if (it.bitmap.compress(Bitmap.CompressFormat.JPEG, 80, FileOutputStream(file))) {
                isSending = true
                object : UploadImageTask(file) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        restoreState = true
    }

    override fun onDestroy() {
        super.onDestroy()
        restoreState = false
    }

    override fun onPause() {
        super.onPause()
        isSending = false
    }
}

