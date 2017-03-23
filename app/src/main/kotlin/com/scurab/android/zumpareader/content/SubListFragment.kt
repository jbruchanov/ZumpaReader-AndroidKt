package com.scurab.android.zumpareader.content

import android.app.ProgressDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.content.post.PostFragment
import com.scurab.android.zumpareader.event.LoadThreadEvent
import com.scurab.android.zumpareader.model.*
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.text.appendReply
import com.scurab.android.zumpareader.text.containsAuthor
import com.scurab.android.zumpareader.ui.hideAnimated
import com.scurab.android.zumpareader.ui.isVisible
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.*
import com.scurab.android.zumpareader.widget.PostMessageView
import com.scurab.android.zumpareader.widget.SurveyView
import com.squareup.otto.Subscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.find
import org.jetbrains.anko.support.v4.toast

/**
 * Created by JBruchanov on 27/11/2015.
 */
class SubListFragment : BaseFragment(), SubListAdapter.ItemClickListener, SendingFragment, SurveyView.ItemClickListener {

    companion object {
        private val ARG_THREAD_ID: String = "ARG_THREAD_ID"
        private val ARG_SCROLL_DOWN: String = "ARG_SCROLL_DOWN"
        private val SCROLL_UP = -1
        private val SCROLL_NONE = 0
        private val SCROLL_DOWN = 1

        fun newInstance(threadId: String, scrollDown: Boolean = false): SubListFragment {
            return SubListFragment().apply {
                var args = Bundle()
                args.putString(ARG_THREAD_ID, threadId)
                args.putBoolean(ARG_SCROLL_DOWN, scrollDown)
                arguments = args
            }
        }
    }

    override val title: CharSequence get() {
        val subject = zumpaData[argThreadId]?.subject
        return if (subject != null) ZumpaSimpleParser.parseBody(subject, context, ImageSpan.ALIGN_BASELINE) else context.getString(R.string.app_name)
    }

    protected val argThreadId: String get() = arguments?.getString(ARG_THREAD_ID) ?: ""
    protected val argScrollDown: Boolean get() = arguments?.getBoolean(ARG_SCROLL_DOWN) ?: false
    private var firstLoad: Boolean = true

    private val recyclerView: RecyclerView? get() = view?.find<RecyclerView>(R.id.recycler_view)
    private val swipyRefreshLayout: SwipyRefreshLayout? get() = view?.find<SwipyRefreshLayout>(R.id.swipe_refresh_layout)
    private val postMessageView: PostMessageView? get() = view?.find<PostMessageView>(R.id.response_panel)
    private val contextColorText: Int by lazy { context.obtainStyledColor(R.attr.contextColorText2) }
    private val treeViewObserver: ViewTreeObserver.OnGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateRecycleViewPadding() }

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

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var content = inflater!!.inflate(R.layout.view_recycler_refreshable_thread, container, false)
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
            openFragment(PostFragment.newInstance(title.toString(), postMessageView!!.message.text.toString(), null, argThreadId, flag))
        }
    }

    override fun onResume() {
        super.onResume()
        mainActivity.exec {
            it.setScrollStrategyEnabled(false)
            if (isLoggedIn) {
                if (postMessageView?.isVisible() ?: false) {
                    it.floatingButton.hideAnimated()
                } else {
                    it.floatingButton.showAnimated()
                }
            }
        }
        view!!.viewTreeObserver.addOnGlobalLayoutListener(treeViewObserver)
    }

    private fun updateRecycleViewPadding() {
        if (zumpaApp?.zumpaPrefs?.isLoggedInNotOffline ?: false) {
            view!!.post {
                //set padding for response panel
                recyclerView.execOn {
                    setPadding(paddingLeft, paddingTop, paddingRight, postMessageView?.height ?: 0)
                }
            }
        }
    }

    override fun onPause() {
        mainActivity?.setScrollStrategyEnabled(true)
        isLoading = false
        isSending = false
        view!!.viewTreeObserver.removeGlobalLayoutListenerSafe(treeViewObserver)
        super.onPause()
    }

    protected fun dispatchSend() {
        var msg = postMessageView?.message?.text?.toString() ?: ""
        if (msg.length == 0) {
            context.toast(R.string.err_empty_msg)
            return
        }

        zumpaApp?.zumpaAPI.exec {
            val app = zumpaApp!!
            val body = ZumpaThreadBody(app.zumpaPrefs.nickName, app.zumpaData[argThreadId]?.subject ?: "", msg, argThreadId)
            val observable = it.sendResponse(argThreadId, argThreadId, body)
            isSending = true
            context.hideKeyboard(view)
            observable
                    .subscribeOn(Schedulers.io())
                    .compose(bindToLifecycle<ZumpaThreadResult>())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                hideMessagePanel(true)
                                loadData(SCROLL_DOWN)
                                isSending = false
                            },
                            { err ->
                                err?.message?.exec { toast(it) }
                                isLoading = false
                                isSending = false
                            }
                    )
        }
    }

    @Subscribe
    fun onLoadThreadEvent(event: LoadThreadEvent) {
        val sameThread = argThreadId == event.id
        if (!sameThread) {
            arguments.putString(ARG_THREAD_ID, event.id)
        }
        loadData(event.id, true, if(sameThread) SCROLL_NONE else SCROLL_UP)
    }

    fun loadData(scrollWay: Int = SCROLL_NONE) {
        loadData(argThreadId, false, scrollWay)
    }

    fun loadData(tid: String, force: Boolean = false, scrollWay: Int = SCROLL_NONE) {
        if ((isLoading && !force) || tid.isNullOrEmpty()) {
            isSending = false
            return
        }
        isLoading = true
        zumpaApp?.zumpaAPI?.getThreadPage(tid, tid).exec {
            it.subscribeOn(Schedulers.io())
                    .compose(bindToLifecycle<ZumpaThreadResult>())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                onResultLoaded(result, force)
                                val scrollWayValue = if(argScrollDown && firstLoad) SCROLL_DOWN else scrollWay
                                if (scrollWayValue != 0) {
                                    firstLoad = false
                                    recyclerView.exec {
                                        when(scrollWayValue){
                                            SCROLL_UP -> it.scrollTo(0, 0)
                                            SCROLL_DOWN -> it.smoothScrollToPosition(it.adapter.itemCount - 1)
                                        }
                                    }
                                }
                                isSending = false
                                isLoading = false
                            },
                            { err ->
                                err?.message?.exec { toast(it) }
                                isSending = false
                                isLoading = false
                            }
                    )
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

    private fun onResultLoaded(result: ZumpaThreadResult, clearData: Boolean) {
        result.items.exec {
            var items = it
            storeReadState(result)
            recyclerView.exec {
                val loadImages = zumpaApp?.zumpaPrefs?.loadImages ?: true
                if (it.adapter == null) {
                    recyclerView?.adapter = SubListAdapter(items, loadImages).apply {
                        itemClickListener = this@SubListFragment
                        surveyClickListner = this@SubListFragment
                    }
                } else {
                    (recyclerView?.adapter as SubListAdapter).execOn {
                        this.loadImages = loadImages
                        updateItems(items, clearData)
                    }
                }
            }
        }
    }

    private fun storeReadState(result: ZumpaThreadResult) {
        val zumpaReadStates = zumpaApp?.zumpaReadStates
        zumpaReadStates.exec {
            val size = result.items.size - 1
            if (it.containsKey(argThreadId)) {
                it[argThreadId]!!.count = size//don't count 1st one as it's actual post
            } else {
                it[argThreadId] = ZumpaReadState(argThreadId, size)
            }
        }
    }

    override fun onItemClick(item: ZumpaThreadItem, longClick: Boolean) {
        if (postMessageView != null && zumpaApp?.zumpaPrefs?.isLoggedInNotOffline ?: false) {
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

    override fun onItemClick(item: SurveyItem) {
        if (zumpaApp?.zumpaPrefs?.isLoggedInNotOffline ?: false) {
            zumpaApp?.zumpaAPI?.voteSurvey(ZumpaVoteSurveyBody(item.surveyId, item.id)).exec {
                isSending = true
                it.subscribeOn(Schedulers.io())
                        .compose(bindToLifecycle<ZumpaGenericResponse>())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { result -> loadData() },
                                { err ->
                                    err?.message?.exec { toast(it) }
                                    isSending = false
                                }
                        )
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
