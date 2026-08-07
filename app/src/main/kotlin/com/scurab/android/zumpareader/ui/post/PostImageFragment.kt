package com.scurab.android.zumpareader.ui.post

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.scurab.android.zumpareader.ui.compose.zumpaContent
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 08/01/2016.
 *
 * A tab body of [PostFragment]. Disappears entirely in C4, when the tabs become pager pages.
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

    private val postViewModel: PostViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private val imageUri: Uri
        get() = arguments?.getParcelable(Intent.EXTRA_STREAM) ?: throw NullPointerException("Arguments")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = zumpaContent { PostImageScreen(imageUri, postViewModel::onLinkShared) }
}
