package com.scurab.android.zumpareader.content

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.content.post.PostFragment

/**
 * Created by JBruchanov on 03/02/2016.
 */
class TabletFragment : BaseFragment() {
    override val title: CharSequence?
        get() = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tablet, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        childFragmentManager.beginTransaction().add(R.id.main_list, MainListFragment()).commit()
        childFragmentManager.beginTransaction().add(R.id.sub_list, SubListFragment.newInstance("", false)).commit()
    }

    override fun onFloatingButtonClick() {
        PostFragment().show(fragmentManager, "PostFragment")
    }
}
