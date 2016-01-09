package com.scurab.android.zumpareader.content.post

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
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
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.getRandomCameraFileUri
import com.scurab.android.zumpareader.util.toast
import com.scurab.android.zumpareader.utils.FotoDiskProvider
import com.scurab.android.zumpareader.widget.PostImagePanelView
import com.squareup.picasso.Picasso
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
            Picasso.with(context).load(imageUri).placeholder(SimpleProgressDrawable(context)).into(image)
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