package com.scurab.android.zumpareader.ui.mainlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.scurab.android.zumpareader.ui.BaseFragment
import com.scurab.android.zumpareader.ui.compose.zumpaContent

/**
 * Created by JBruchanov on 24/11/2015.
 *
 * A host for [MainListScreen]. The screen brings its own Scaffold, so the activity's toolbar and fab
 * are dead weight for it - both go when MainActivity does in C7.
 */
open class MainListFragment : BaseFragment() {

    override val title: CharSequence? get() = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = zumpaContent { MainListScreen() }
}
