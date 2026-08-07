package com.scurab.android.zumpareader.ui.post

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.scurab.android.zumpareader.ui.BaseDialogFragment
import com.scurab.android.zumpareader.ui.compose.zumpaContent

/**
 * Created by JBruchanov on 08/01/2016.
 *
 * A host for [PostScreen] and the arguments it was opened with. The fab hiding is the last piece of
 * View chrome here; it goes when MainActivity does in C7.
 */
class PostFragment : BaseDialogFragment() {

    companion object {
        const val THREAD_ID = "THREAD_UD"
        const val PICKER = "PICKER"

        fun newInstance(subject: String?, message: String?, uris: Array<Uri>? = null, threadId: String? = null, picker: PostPicker? = null): PostFragment {
            return PostFragment().apply {
                arguments = arguments(subject, message, uris, threadId, picker)
            }
        }

        fun arguments(subject: String?, message: String?, uris: Array<Uri>? = null, threadId: String? = null, picker: PostPicker? = null): Bundle {
            return Bundle().apply {
                putString(Intent.EXTRA_SUBJECT, subject)
                putString(Intent.EXTRA_TEXT, message)
                putParcelableArray(Intent.EXTRA_STREAM, uris)
                putString(THREAD_ID, threadId)
                putString(PICKER, picker?.name)
            }
        }
    }

    private val args: PostArgs
        get() = PostArgs(
            subject = arguments?.getString(Intent.EXTRA_SUBJECT),
            message = arguments?.getString(Intent.EXTRA_TEXT),
            uris = arguments?.getParcelableArray(Intent.EXTRA_STREAM)?.filterIsInstance<Uri>().orEmpty(),
            threadId = arguments?.getString(THREAD_ID),
        )

    private val picker: PostPicker?
        get() = arguments?.getString(PICKER)?.let { PostPicker.valueOf(it) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = zumpaContent { PostScreen(args, picker) }
}
