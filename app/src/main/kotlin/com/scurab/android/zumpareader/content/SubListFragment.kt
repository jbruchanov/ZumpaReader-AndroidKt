package com.scurab.android.zumpareader.content

import android.graphics.Color
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
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.util.exec
import rx.Observer
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * Created by JBruchanov on 27/11/2015.
 */
public class SubListFragment : BaseFragment() {

    companion object {
        private val THREAD_ID: String = "THREAD_ID"

        public fun newInstance(threadId: String): SubListFragment {
            return SubListFragment().apply {
                var args = Bundle()
                args.putString(THREAD_ID, threadId)
                arguments = args
            }
        }
    }

    override val title: CharSequence get() = zumpaData[threadId]?.subject ?: ""
    protected val threadId: String by lazy { arguments!!.getString(THREAD_ID) }

    private val recyclerView by lazy { view!!.find<RecyclerView>(R.id.recycler_view) }
    private val swipyRefreshLayout by lazy { view!!.find<SwipyRefreshLayout>(R.id.swipe_refresh_layout) }

    override var isLoading: Boolean
        get() = super.isLoading
        set(value) {
            super.isLoading = value
            progressBarVisible = value
            swipyRefreshLayout.exec {
                if (it.isRefreshing) {
                    it.isRefreshing = value
                }
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View? {
        var content = inflater.inflate(R.layout.view_recycler_refreshable, container, false)
        content.setBackgroundColor(Color.BLACK)
        return content
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        swipyRefreshLayout.direction = SwipyRefreshLayoutDirection.BOTTOM
        swipyRefreshLayout.setOnRefreshListener { loadData() }
        loadData()
    }

    override fun onPause() {
        super.onPause()
        isLoading = false
    }

    public fun loadData() {
        if (isLoading) {
            return
        }
        isLoading = true
        var tid = threadId
        zumpaApp?.zumpaAPI?.getThreadPage(tid, tid).exec{
            it.observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(object : Observer<ZumpaThreadResult?> {
                        override fun onNext(t: ZumpaThreadResult?) {
                            t.exec {
                                onResultLoaded(it)
                            }
                        }

                        override fun onError(e: Throwable?) { e?.message?.exec { toast(it) } }
                        override fun onCompleted() { isLoading = false }
                    })
        }
    }

    private fun onResultLoaded(it: ZumpaThreadResult) {
        it.items.exec {
            var items = it
            recyclerView.exec {
                if (it.adapter == null) {
                    recyclerView?.adapter = SubListAdapter(items)
                } else {
                    (recyclerView?.adapter as SubListAdapter).updateItems(items)
                }
            }
        }
    }
}