package com.scurab.android.zumpareader.content

import android.app.ProgressDialog
import android.graphics.Color
import android.net.Uri
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
import com.scurab.android.zumpareader.content.post.PostFragment
import com.scurab.android.zumpareader.model.ZumpaReadState
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.text.appendReply
import com.scurab.android.zumpareader.text.containsAuthor
import com.scurab.android.zumpareader.ui.hideAnimated
import com.scurab.android.zumpareader.ui.isVisible
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.*
import com.scurab.android.zumpareader.widget.PostMessageView
import rx.Observer
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * Created by JBruchanov on 27/11/2015.
 */
public class SubListFragment : BaseFragment(), SubListAdapter.ItemClickListener, SendingFragment {
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

    private val recyclerView: RecyclerView? get() = view?.find<RecyclerView>(R.id.recycler_view)
    private val swipyRefreshLayout: SwipyRefreshLayout? get() = view?.find<SwipyRefreshLayout>(R.id.swipe_refresh_layout)
    private val postMessageView: PostMessageView? get() = view?.find<PostMessageView>(R.id.response_panel)
    private var scrollDownAfterLoad: Boolean = false
    private val contextColorText: Int by lazy { context.obtainStyledColor(R.attr.contextColorText) }

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

    override var sendingDialog: ProgressDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View? {
        var content = inflater.inflate(R.layout.view_recycler_refreshable_thread, container, false)
        content.setBackgroundColor(Color.BLACK)
        return content
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView?.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        swipyRefreshLayout?.direction = SwipyRefreshLayoutDirection.BOTTOM
        swipyRefreshLayout?.setOnRefreshListener { loadData() }
        postMessageView.execOn {
            visibility = View.INVISIBLE
            sendButton.setOnClickListener { dispatchSend() }
            camera.setOnClickListener { dispatchOpenPostMessage(R.id.camera) }
            photo.setOnClickListener { dispatchOpenPostMessage(R.id.photo) }

        }
        loadData()
    }

    protected fun dispatchOpenPostMessage(flag: Int) {
        mainActivity.execOn {
            openFragment(PostFragment.newInstance(title.toString(), postMessageView!!.message.text.toString(), null, threadId, flag))
        }
    }

    override fun onResume() {
        super.onResume()
        mainActivity.exec {
            it.setScrollStrategyEnabled(false)
            if (isLoggedIn) {
                it.floatingButton.showAnimated()
            }
            it.settingsButton.visibility = View.GONE
        }
        if (zumpaApp?.zumpaPrefs?.isLoggedIn ?: false) {
            view.post { //set padding for response panel
                recyclerView.execOn {
                    setPadding(paddingLeft, paddingTop, paddingRight, postMessageView?.height ?: 0)
                }
            }
        }
    }

    override fun onPause() {
        mainActivity?.setScrollStrategyEnabled(true)
        mainActivity?.settingsButton?.visibility = View.VISIBLE
        isLoading = false
        isSending = false
        super.onPause()
    }

    protected fun dispatchSend() {
        var msg = postMessageView?.message?.text?.toString()?.trim() ?: ""
        if (msg.length == 0) {
            context.toast(R.string.err_empty_msg)
            return
        }

        zumpaApp?.zumpaAPI.exec {
            val app = zumpaApp!!
            val body = ZumpaThreadBody(app.zumpaPrefs.nickName, app.zumpaData[threadId]?.subject ?: "", msg, threadId)
            val observable = it.sendResponse(threadId, threadId, body)
            isSending = true
            context.hideKeyboard(view)
            observable
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : Observer<ZumpaThreadResult?> {
                        override fun onNext(t: ZumpaThreadResult?) {
                            t.exec {
                                if (isResumed) {
                                    hideMessagePanel(true)
                                    isSending = false
                                    isLoading = false
                                    scrollDownAfterLoad = true
                                    loadData()
                                }
                            }
                        }

                        override fun onError(e: Throwable?) {
                            if (isResumed) {
                                e?.message?.exec { toast(it) }
                                isSending = false
                            }
                        }

                        override fun onCompleted() {
                        }
                    })
        }
    }

    public fun loadData() {
        if (isLoading) {
            return
        }
        isLoading = true
        var tid = threadId
        zumpaApp?.zumpaAPI?.getThreadPage(tid, tid).exec {
            it.observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(object : Observer<ZumpaThreadResult?> {
                        override fun onNext(t: ZumpaThreadResult?) {
                            t.exec {
                                onResultLoaded(it)
                                if (scrollDownAfterLoad) {
                                    scrollDownAfterLoad = false
                                    recyclerView.exec {
                                        it.smoothScrollToPosition(it.adapter.itemCount)
                                    }
                                }
                            }
                        }

                        override fun onError(e: Throwable?) {
                            e?.message?.exec { toast(it) }
                        }

                        override fun onCompleted() {
                            isLoading = false
                        }
                    })
        }
    }

    override fun onFloatingButtonClick() {
        postMessageView.exec {
            if (!it.isVisible()) {
                it.showAnimated()
            }
            mainActivity?.floatingButton?.hideAnimated()
        }
    }

    override fun onBackButtonClick(): Boolean {
        if (isLoggedIn) {
            if (hideMessagePanel()) {
                return true
            }
        }
        return super.onBackButtonClick()
    }

    fun hideMessagePanel(clearText: Boolean = false): Boolean {
        if (clearText) {
            postMessageView?.message?.text = null
        }
        postMessageView.exec {
            if (it.isVisible()) {
                it.hideAnimated()
                mainActivity?.floatingButton?.showAnimated()
                return true
            }
        }
        return false
    }

    private fun onResultLoaded(result: ZumpaThreadResult) {
        result.items.exec {
            var items = it
            storeReadState(result)
            recyclerView.exec {
                val loadImages = zumpaApp?.zumpaPrefs?.loadImages ?: true
                if (it.adapter == null) {
                    recyclerView?.adapter = SubListAdapter(items, loadImages).apply {
                        itemClickListener = this@SubListFragment
                    }
                } else {
                    (recyclerView?.adapter as SubListAdapter).execOn {
                        this.loadImages = loadImages
                        updateItems(items)
                    }
                }
            }
        }
    }

    private fun storeReadState(result: ZumpaThreadResult) {
        val zumpaReadStates = zumpaApp?.zumpaReadStates
        zumpaReadStates.exec {
            val size = result.items.size - 1
            if (it.containsKey(threadId)) {
                it[threadId]!!.count = size//don't count 1st one as it's actual post
            } else {
                it[threadId] = ZumpaReadState(threadId, size)
            }
        }
    }

    override fun onItemClick(item: ZumpaThreadItem, longClick: Boolean) {
        if (postMessageView != null && zumpaApp?.zumpaPrefs?.isLoggedIn ?: false) {
            val postMessageView = this.postMessageView!!
            if (longClick) {
                postMessageView.showAnimated()
                mainActivity?.floatingButton?.hideAnimated()
            }
            if (postMessageView.isVisible()) {
                postMessageView.message.text.execOn {
                    var text = "@%s: \n".format(item.authorReal)
                    val outRange = OutRef<IntRange>()
                    if (containsAuthor(text, outRange)) {
                        val range = outRange.data!!
                        replace(range.first, range.last, "")
                    } else {
                        appendReply(text, contextColorText)
                    }
                }
            }
        }
    }

    override fun onItemClick(url: String, longClick: Boolean) {
        if (longClick) {
            context.saveToClipboard(Uri.parse(url))
            context.toast(R.string.saved_into_clipboard)
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