package com.scurab.android.zumpareader.ui.sublist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.scurab.android.zumpareader.ui.BaseFragment
import com.scurab.android.zumpareader.ui.compose.zumpaContent

/**
 * Created by JBruchanov on 27/11/2015.
 *
 * A host for [SubListScreen] and the thread id it was opened with.
 */
class SubListFragment : BaseFragment() {

    companion object {
        private const val ARG_THREAD_ID: String = "ARG_THREAD_ID"
        private const val ARG_SCROLL_DOWN: String = "ARG_SCROLL_DOWN"

        fun newInstance(threadId: String, scrollDown: Boolean = false): SubListFragment {
            return SubListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_THREAD_ID, threadId)
                    putBoolean(ARG_SCROLL_DOWN, scrollDown)
                }
            }
        }
    }

    private val argThreadId: String get() = arguments?.getString(ARG_THREAD_ID) ?: ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = zumpaContent { SubListScreen(argThreadId) }
}
