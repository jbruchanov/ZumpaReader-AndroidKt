package com.scurab.android.zumpareader.ui.post

import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.arch.collectWhileStarted
import com.scurab.android.zumpareader.drawable.SimpleProgressDrawable
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.ui.SendingDialogController
import com.scurab.android.zumpareader.util.asVisibility
import com.scurab.android.zumpareader.util.saveToClipboard
import com.scurab.android.zumpareader.widget.PostImagePanelView
import com.squareup.picasso.Picasso
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 08/01/2016.
 */
class PostImageFragment : Fragment() {

    companion object {
        fun newInstance(uri: Uri): PostImageFragment {
            return PostImageFragment().apply { arguments = arguments(uri) }
        }

        fun arguments(uri: Uri): Bundle {
            return Bundle().apply { putParcelable(Intent.EXTRA_STREAM, uri) }
        }
    }

    private val viewModel: PostImageViewModel by viewModel()
    private val postViewModel: PostViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private val image: ImageView get() = requireView().findViewById(R.id.image)
    private val imagePanel: PostImagePanelView get() = requireView().findViewById(R.id.post_image_panel_view)
    private val sendingDialog by lazy { SendingDialogController(requireContext()) }

    private val imageUri: Uri
        get() = arguments?.getParcelable(Intent.EXTRA_STREAM) ?: throw NullPointerException("Arguments")

    private var loadedThumbnail: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_post_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imagePanel.upload.setOnClickListener { viewModel.onUploadClick() }
        imagePanel.resize.setOnClickListener { viewModel.onResizeClick() }
        imagePanel.rotateRight.setOnClickListener { viewModel.onRotateClick() }
        imagePanel.copy.setOnClickListener { onCopyLinkToClipboard() }
        imagePanel.sizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.onSampleSizeSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        viewModel.uiState
            .map { it.thumbnailPath }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { path ->
                if (path != null && path != loadedThumbnail) {
                    loadedThumbnail = path
                    Picasso.get()
                        .load(File(path))
                        .placeholder(SimpleProgressDrawable(requireContext()))
                        .into(image)
                }
            }

        viewModel.uiState
            .map { it.original }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { meta ->
                imagePanel.setImageSize(meta?.let { Point(it.width, it.height) }, meta?.bytes ?: 0L)
            }

        viewModel.uiState
            .map { it.resized }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { meta ->
                meta?.let { imagePanel.setResizedImageSize(Point(it.width, it.height), it.bytes) }
            }

        viewModel.uiState
            .map { it.rotationDegrees }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { image.animate().rotation(it.toFloat()) }

        viewModel.uiState
            .map { it.uploadedLink != null }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { imagePanel.copy.visibility = it.asVisibility() }

        viewModel.uiState
            .map { it.isBusy }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { sendingDialog.update(it) }

        viewModel.effects.collectWhileStarted(viewLifecycleOwner) { onEffect(it) }

        viewModel.start(imageUri)
    }

    private fun onEffect(effect: UiEffect) {
        when (effect) {
            is PostImageEffect.ImageUploaded -> {
                //the parent's draft is where the link belongs, and it switches to the message tab
                postViewModel.onLinkShared(effect.link)
                toast(R.string.done)
            }

            is ShowToast -> effect.text?.let { toast(it) } ?: toast(effect.resId)
            else -> Unit
        }
    }

    private fun onCopyLinkToClipboard() {
        viewModel.uiState.value.uploadedLink?.let {
            requireContext().saveToClipboard(Uri.parse(it))
            toast(R.string.saved_into_clipboard)
        }
    }

    override fun onDestroyView() {
        sendingDialog.update(false)
        super.onDestroyView()
    }
}
