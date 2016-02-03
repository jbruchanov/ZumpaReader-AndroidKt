package com.scurab.android.zumpareader.content

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.model.ZumpaThread

/**
 * Created by JBruchanov on 03/02/2016.
 */
public class TabletFragment : BaseFragment() {
    override val title: CharSequence?
        get() = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tablet, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val subListFragment = SubListFragment.newInstance("", false)
        childFragmentManager.beginTransaction().add(R.id.main_list, object : MainListFragment() {
            override fun onThreadItemClick(item: ZumpaThread, position: Int) {
                subListFragment.loadData(item.id, true)
            }
        }).commit()
        childFragmentManager.beginTransaction().add(R.id.sub_list, subListFragment).commit()
    }

    override fun onFloatingButtonClick() {
        //not implemented yet
    }
}