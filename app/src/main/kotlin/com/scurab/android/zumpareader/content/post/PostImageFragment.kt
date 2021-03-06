package com.scurab.android.zumpareader.content.post

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.content.SendingFragment
import com.scurab.android.zumpareader.content.post.tasks.CopyFromResourcesTask
import com.scurab.android.zumpareader.content.post.tasks.ProcessImageTask
import com.scurab.android.zumpareader.drawable.SimpleProgressDrawable
import com.scurab.android.zumpareader.extension.app
import com.scurab.android.zumpareader.util.asVisibility
import com.scurab.android.zumpareader.util.saveToClipboard
import com.scurab.android.zumpareader.util.toast
import com.scurab.android.zumpareader.widget.PostImagePanelView
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle2.components.support.RxFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.jetbrains.anko.find
import org.jetbrains.anko.support.v4.toast
import java.io.File
import java.lang.NullPointerException


/**
 * Created by JBruchanov on 08/01/2016.
 */
class PostImageFragment : RxFragment(), SendingFragment {

    companion object {
        fun newInstance(uri: Uri): PostImageFragment {
            return PostImageFragment().apply {
                arguments = arguments(uri)
            }
        }

        fun arguments(uri: Uri): Bundle {
            return Bundle().apply {
                putParcelable(Intent.EXTRA_STREAM, uri)
            }
        }
    }

    override var sendingDialog: ProgressDialog? = null

    private val image: ImageView get() {
        return view!!.find(R.id.image)
    }

    private val imagePanel: PostImagePanelView get() {
        return view!!.find(R.id.post_image_panel_view)
    }

    private val imageUri by lazy { arguments?.getParcelable<Uri>(Intent.EXTRA_STREAM) ?: throw NullPointerException("Arguments") }
    private var imageFile: String? = null
    private var imageRotation = 0
    private var restoreState = false

    private var imageResolution: Point? = null
    private var imageSize: Long = 0
    private var imageResizedResolution: Point? = null
    private var imageResizedSize: Long = 0
    private var imageUploadedLink: String? = null

    private val imageFileToUpload: String? get() {
        return if (imageFile != null) imageFile + "_out" else null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_post_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        try {
            CopyFromResourcesTask(view.context, imageUri)
                    .compose(bindToLifecycle())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { result, err ->
                        if (result != null) {
                            imageFile = result.imageFile!!.absolutePath
                            if (!restoreState) {
                                this@PostImageFragment.imageSize = result.imageSize
                                this@PostImageFragment.imageResolution = result.imageResolution
                                imagePanel.setImageSize(this.imageResolution, imageSize)
                            }
                            Picasso.with(context).load(result.thumbnail).placeholder(SimpleProgressDrawable(context)).into(image)
                        } else {
                            toast(err.message.toString())
                        }
                    }
            if (restoreState) {
                imagePanel.setImageSize(imageResolution, imageSize)
                if (imageResizedResolution != null) {
                    imagePanel.setResizedImageSize(imageResizedResolution!!, imageResizedSize)
                }
                image.rotation = imageRotation.toFloat()
                imagePanel.copy.visibility = (imageUploadedLink != null).asVisibility()
            }
        } catch (e: Throwable) {
            context.toast(e.message)
        }
        imagePanel.upload.setOnClickListener { dispatchUpload() }
        imagePanel.resize.setOnClickListener { onImageResize() }
        imagePanel.rotateRight.setOnClickListener { onImageRotate() }
        imagePanel.copy.setOnClickListener { onCopyLinkToClipboard() }
    }

    protected fun onCopyLinkToClipboard() {
        val context = requireContext()
        imageUploadedLink.let {
            context.saveToClipboard(Uri.parse(it))
            context.toast(R.string.saved_into_clipboard)
        }
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
        imageFile?.let {
            ProcessImageTask(it, imageFileToUpload!!, inSample, imageRotation)
                    .compose(bindToLifecycle())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { result, err ->
                        isSending = false
                        if (result != null) {
                            image.animate().rotation(imageRotation.toFloat())
                            imageResizedResolution = result.imageResolution!!
                            imageResizedSize = result.imageSize
                            imagePanel.setResizedImageSize(result.imageResolution!!, result.imageSize)
                        }
                        if (err != null) {
                            toast(err.message.toString())
                        }
                    }
        }
    }

    private fun dispatchUpload() {
        var out = File(imageFileToUpload)
        if (!out.exists()) {
            out = File(imageFile)
        }

        isSending = true
        val reqFile = RequestBody.create(MediaType.parse("image/*"), out)
        val body = MultipartBody.Part.createFormData("image", out.name, reqFile)
        val name = RequestBody.create(MediaType.parse("text/plain"), "Submit")

        val context = requireContext()
        app().zumpaPHPAPI.postImage(body, name)
                .compose(bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    isSending = false
                    val url = it.asUTFString()
                    if (url.isNotEmpty()) {
                        imageUploadedLink = url
                        dispatchImageUploaded(url)
                    } else {
                        context.toast(R.string.err_fail)
                    }
                }, { err ->
                    err.printStackTrace()
                    context.toast(R.string.err_fail)
                })
    }

    protected fun dispatchImageUploaded(result: String) {
        (parentFragment as? PostFragment)?.apply {
            onSharedImage(result)
            requireContext().toast(R.string.done)
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

