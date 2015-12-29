package com.scurab.android.zumpareader.content

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import com.pawegio.kandroid.find
import com.pawegio.kandroid.toast
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.ui.hideAnimated
import com.scurab.android.zumpareader.ui.isVisible
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.obtainStyledColor
import com.scurab.android.zumpareader.util.toast
import rx.Observer
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * Created by JBruchanov on 27/11/2015.
 */
public class SubListFragment : BaseFragment(), SubListAdapter.ItemClickListener {

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

    override val title: CharSequence get() {
        val subject = zumpaData[threadId]?.subject
        return if (subject != null) ZumpaSimpleParser.parseBody(subject, context) else ""
    }
    
    protected val threadId: String by lazy { arguments!!.getString(THREAD_ID) }

    private val recyclerView: RecyclerView get() = view!!.find<RecyclerView>(R.id.recycler_view)
    private val swipyRefreshLayout: SwipyRefreshLayout get() = view!!.find<SwipyRefreshLayout>(R.id.swipe_refresh_layout)
    private val responsePanel: View get() = view!!.find<View>(R.id.response_panel)

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
        var content = inflater.inflate(R.layout.view_recycler_refreshable_thread, container, false)
        content.setBackgroundColor(Color.BLACK)
        initIcons(content)
        return content
    }

    private fun initIcons(content: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val color = context.obtainStyledColor(R.attr.contextColor)
            updateTint(content.find(R.id.photo), color)
            updateTint(content.find(R.id.camera), color)
            updateTint(content.find(R.id.survey), color)
            updateTint(content.find(R.id.send), color)
        }
    }

    private fun updateTint(imageView: ImageView, color: Int) {
        var drawable = DrawableCompat.wrap(imageView.drawable);
        DrawableCompat.setTintList(drawable, ColorStateList.valueOf(color));
        imageView.setImageDrawable(drawable)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        responsePanel.visibility = View.INVISIBLE
        recyclerView.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        swipyRefreshLayout.direction = SwipyRefreshLayoutDirection.BOTTOM
        swipyRefreshLayout.setOnRefreshListener { loadData() }
        loadData()
    }

    override fun onResume() {
        super.onResume()
        mainActivity.exec {
            it.setScrollStrategyEnabled(false)
            it.floatingButton.showAnimated()
        }
        view.post {//set padding for response panel
            recyclerView.setPadding(recyclerView.paddingLeft, recyclerView.paddingTop, recyclerView.paddingRight, responsePanel.height)
        }
    }

    override fun onPause() {
        mainActivity?.setScrollStrategyEnabled(true)
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

    override fun onFloatingButtonClick() {
        if (!responsePanel.isVisible()) {
            responsePanel.showAnimated()
            mainActivity?.floatingButton?.hideAnimated()
        }
    }

    override fun onBackButtonClick(): Boolean {
        if (responsePanel.isVisible()) {
            responsePanel.hideAnimated()
            mainActivity?.floatingButton?.showAnimated()
            return true
        }
        return super.onBackButtonClick()
    }

    private fun onResultLoaded(it: ZumpaThreadResult) {
        it.items.exec {
            var items = it
            recyclerView.exec {
                if (it.adapter == null) {
                    recyclerView?.adapter = SubListAdapter(items).apply {
                        itemClickListener = this@SubListFragment
                    }
                } else {
                    (recyclerView?.adapter as SubListAdapter).updateItems(items)
                }
            }
        }
    }

    override fun onItemClick(item: ZumpaThreadItem, longClick: Boolean) {
        if (longClick) {

        } else {

        }
    }

    override fun onItemClick(url: String, longClick: Boolean) {
        if (longClick) {
            if (saveIntoClipboard(url)) {
                context.toast(R.string.saved_into_clipboard)
            }
        } else {
            val id = ZumpaSimpleParser.getZumpaThreadId(url)
            if (id != 0) {
                openFragment(SubListFragment.newInstance(id.toString()), true, true)
            } else {
                startLinkActivity(url)
            }
        }
    }
}