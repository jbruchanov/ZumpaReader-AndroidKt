package com.scurab.android.zumpareader.content

import android.app.ProgressDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import com.pawegio.kandroid.find
import com.pawegio.kandroid.toast
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
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
    private val postMessageView: PostMessageView get() = view!!.find<PostMessageView>(R.id.response_panel)
    private var scrollDownAfterLoad : Boolean = false

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

    private var sendingDialog: ProgressDialog? = null
    private var isSending: Boolean
        get() {
            return sendingDialog != null
        }
        set(value) {
            if (value != isSending) {
                if (value) {
                    context.exec {
                        sendingDialog = ProgressDialog.show(context, null, it.resources.getString(R.string.wheeeee), true, false)
                    }
                } else {
                    sendingDialog.exec {
                        it.dismiss()
                    }
                    sendingDialog = null
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
        imageView.setImageDrawable(imageView.drawable.wrapWithTint(color))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postMessageView.visibility = View.INVISIBLE
        recyclerView.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        swipyRefreshLayout.direction = SwipyRefreshLayoutDirection.BOTTOM
        swipyRefreshLayout.setOnRefreshListener { loadData() }
        postMessageView.sendButton.setOnClickListener { dispatchSend() }
        loadData()
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
        view.post {//set padding for response panel
            recyclerView.setPadding(recyclerView.paddingLeft, recyclerView.paddingTop, recyclerView.paddingRight, postMessageView?.height ?: 0)
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
        var msg = postMessageView.message.text.toString().trim()
        if (msg.length == 0) {
            context.toast(R.string.err_empty_msg)
            return
        }

        zumpaApp?.zumpaAPI.exec {
            val app = zumpaApp!!
            val body = ZumpaThreadBody(app.zumpaPrefs.nickName, app.zumpaData[threadId]?.subject ?: "", msg, threadId)
            val observable = it.sendResponse(threadId, threadId, body)
            isSending = true
            hideKeyboard()
            observable
                    .subscribeOn(Schedulers.io())
                    .subscribe(object : Observer<ZumpaThreadResult?> {
                        override fun onNext(t: ZumpaThreadResult?) {
                            t.exec {
                                view.post {
                                    hideMessagePanel(true)
                                    isSending = false
                                    isLoading = false
                                    scrollDownAfterLoad = true
                                    loadData()
                                }
                            }
                        }
                        override fun onError(e: Throwable?) {
                            e?.message?.exec { toast(it) }
                            isSending = false
                        }
                        override fun onCompleted() { }
                    })
        }
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
                                if (scrollDownAfterLoad) {
                                    scrollDownAfterLoad = false
                                    recyclerView.scrollToPosition(recyclerView.adapter.itemCount)
                                }
                            }
                        }

                        override fun onError(e: Throwable?) { e?.message?.exec { toast(it) } }
                        override fun onCompleted() { isLoading = false }
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

    fun hideMessagePanel(clearText: Boolean = false) : Boolean {
        if (clearText) {
            postMessageView.message.text = null
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

    private fun onResultLoaded(it: ZumpaThreadResult) {
        it.items.exec {
            var items = it
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