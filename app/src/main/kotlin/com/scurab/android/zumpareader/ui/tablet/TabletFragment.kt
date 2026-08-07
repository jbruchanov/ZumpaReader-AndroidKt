package com.scurab.android.zumpareader.ui.tablet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.scurab.android.zumpareader.ui.BaseFragment
import com.scurab.android.zumpareader.ui.compose.zumpaContent

/**
 * Created by JBruchanov on 03/02/2016.
 */
class TabletFragment : BaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = zumpaContent { TwoPaneScreen() }
}
