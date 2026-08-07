package com.scurab.android.zumpareader.ui.post

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.scurab.android.zumpareader.ui.compose.zumpaContent
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 31/12/2015.
 *
 * A tab body of [PostFragment], bound to the parent's [PostViewModel]. Disappears entirely in C4,
 * when the tabs become pager pages and there is nothing left to host.
 */
class PostMessageFragment : Fragment() {

    private val viewModel: PostViewModel by viewModel(ownerProducer = { requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = zumpaContent { PostMessageScreen(viewModel) }
}
