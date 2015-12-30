package com.scurab.android.zumpareader.content

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import com.pawegio.kandroid.find
import com.pawegio.kandroid.toast
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.util.asListOfValues
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.execIfNull
import com.yqritc.recyclerviewflexibledivider.HorizontalDividerItemDecoration
import rx.Observer
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class MainListFragment : BaseFragment(), MainListAdapter.OnShowItemListener {

    private var content: View? = null
    private val recyclerView: RecyclerView get() = content!!.find<RecyclerView>(R.id.recycler_view)
    private val swipeToRefresh: SwipyRefreshLayout get() = content!!.find<SwipyRefreshLayout>(R.id.swipe_refresh_layout)

    private var nextPageId: String? = null
    override var isLoading: Boolean
        get() = super.isLoading
        set(value) {
            super.isLoading = value
            progressBarVisible = value
            swipeToRefresh?.exec {
                if (it.isRefreshing) {
                    it.isRefreshing = value
                }
            }
        }

    override val title: CharSequence get() = getString(R.string.app_name)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View? {
        content.execIfNull {
            content = inflater.inflate(R.layout.view_recycler_refreshable, container, false)
            content.exec {
                swipeToRefresh.direction = SwipyRefreshLayoutDirection.TOP
                recyclerView.apply {
                    layoutManager = LinearLayoutManager(inflater.context, LinearLayoutManager.VERTICAL, false)
                    addItemDecoration(HorizontalDividerItemDecoration.Builder(inflater.context)
                            .color(resources.getColor(R.color.gray))
                            .sizeResId(R.dimen.divider)
                            .showLastDivider()
                            .build())
                    //itemAnimator = DefaultItemAnimator()
                }
            }
        }

        return content;
    }

    override fun onDestroyView() {
        super.onDestroyView()
        content.exec {
            (it.parent as? ViewGroup)?.removeView(content)
        }
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeToRefresh.setOnRefreshListener { loadPage() }
        recyclerView.adapter.execIfNull {
            loadPage()
        }
    }

    private fun loadPage(fromThread: String? = null) {
        if (isLoading) {
            return
        }
        isLoading = true
        val mainPage = if (fromThread != null) zumpaApp?.zumpaAPI?.getMainPage(fromThread) else zumpaApp?.zumpaAPI?.getMainPage()
        mainPage.exec{
            it.observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe(object : Observer<ZumpaMainPageResult?> {
                override fun onNext(t: ZumpaMainPageResult?) {
                    t.exec {
                        onResultLoaded(it)
                    }
                }

                override fun onError(e: Throwable?) {
                    isLoading = false
                    e?.message?.exec { toast(it) }
                }
                override fun onCompleted() { isLoading = false }
            })
        }
    }

    protected fun onResultLoaded(response: ZumpaMainPageResult?) {
        response?.exec {
            zumpaData.putAll(it.items)
            nextPageId = it.nextPage
            val values = it.items.asListOfValues()
            recyclerView.exec {
                if (it.adapter != null) {
                    (it.adapter as MainListAdapter).addItems(values, zumpaApp?.zumpaPrefs?.loggedUserName)
                } else {
                    val mainListAdapter = MainListAdapter(values)
                    mainListAdapter.setOnShowItemListener(this@MainListFragment, 15)
                    mainListAdapter.onItemClickListener = object : MainListAdapter.OnItemClickListener {
                        override fun onItemClick(item: ZumpaThread, position: Int) {
                            onThreadItemClick(item, position)
                        }
                    }
                    it.adapter = mainListAdapter
                }
            }
            isLoading = false
        }
    }

    public fun onThreadItemClick(item: ZumpaThread, position: Int) {
        isLoading = false
        item.state = ZumpaThread.STATE_NONE
        recyclerView?.adapter.exec {
            it.notifyItemChanged(position)
        }
        openFragment(SubListFragment.newInstance(item.id.toString()), true, true)
    }

    override fun onShowingItem(source: MainListAdapter, item: Int) {
        if (!isLoading) {
            (recyclerView?.adapter as MainListAdapter).exec {
                var v = it.items.last()
                loadPage(v.id)
            }

        }
    }
}