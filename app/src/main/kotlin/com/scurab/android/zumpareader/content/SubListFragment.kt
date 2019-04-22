package com.scurab.android.zumpareader.content

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.app.ImageActivity
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
import androidx.core.app.ActivityOptionsCompat
import androidx.core.text.TextUtilsCompat
import android.text.ClipboardManager
import android.text.TextUtils
import com.scurab.android.zumpareader.content.SubListAdapter.Companion.tReply
import com.scurab.android.zumpareader.extension.app
import com.scurab.android.zumpareader.widget.ToggleAdapter


/**
 * Created by JBruchanov on 27/11/2015.
 */
class SubListFragment : BaseFragment(), SubListAdapter.ItemClickListener, SendingFragment, SurveyView.ItemClickListener, IsReloadable {

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
        return if (subject != null) ZumpaSimpleParser.parseBody(subject, context, ImageSpan.ALIGN_BASELINE) else getString(R.string.app_name)
    }

    protected val argThreadId: String get() = arguments?.getString(ARG_THREAD_ID) ?: ""
    protected val argScrollDown: Boolean get() = arguments?.getBoolean(ARG_SCROLL_DOWN) ?: false
    private var firstLoad: Boolean = true

    private val recyclerView: RecyclerView? get() = view?.find(R.id.recycler_view)
    private val swipyRefreshLayout: SwipyRefreshLayout? get() = view?.find(R.id.swipe_refresh_layout)
    private val postMessageView: PostMessageView? get() = view?.find(R.id.response_panel)
    private val contextColorText: Int by lazy { requireContext().obtainStyledColor(R.attr.contextColorText2) }
    private val treeViewObserver: ViewTreeObserver.OnGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateRecycleViewPadding() }
    private lateinit var delegate: BehaviourDelegate

    override var isLoading: Boolean
        get() = super.isLoading
        set(value) {
            super.isLoading = value
            progressBarVisible = value
            swipyRefreshLayout?.let {
                if (it.isRefreshing) {
                    it.isRefreshing = value
                }
            }
        }

    override var sendingDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        delegate = if (isTablet) TabletBehaviour(this) else PhoneBehaviour(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val content = inflater.inflate(R.layout.view_recycler_refreshable_thread, container, false)
        content.setBackgroundColor(Color.BLACK)
        return content
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView?.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        swipyRefreshLayout?.apply {
            direction = SwipyRefreshLayoutDirection.BOTTOM
            setOnRefreshListener { loadData() }
            setColorSchemeColors(context.getColorFromTheme(R.attr.contextColor))
        }
        postMessageView?.apply {
            addButton.visibility = isTabletVisibility
            addButton.setOnClickListener { dispatchOpenPostMessage() }
            sendButton.setOnClickListener { dispatchSend() }
            camera.setOnClickListener { dispatchOpenPostMessage(R.id.camera) }
            photo.setOnClickListener { dispatchOpenPostMessage(R.id.photo) }
            giphy.setOnClickListener { dispatchOpenPostMessage(R.id.giphy) }
        }
        delegate.onViewCreated()
        loadData()
    }


    protected fun dispatchOpenPostMessage(flag: Int? = null) {
        delegate.openPostFragment(flag)
    }

    override fun onResume() {
        super.onResume()
        mainActivity?.let {
            it.setScrollStrategyEnabled(false)
            delegate.onResume()
        }
        view!!.viewTreeObserver.addOnGlobalLayoutListener(treeViewObserver)
    }

    private fun updateRecycleViewPadding() {
        if (zumpaApp.zumpaPrefs.isLoggedInNotOffline ?: false) {
            view!!.post {
                //set padding for response panel
                recyclerView?.apply {
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
        val context = requireContext()
        if (msg.isEmpty()) {
            context.toast(R.string.err_empty_msg)
            return
        }

        app().zumpaAPI.let {
            val app = zumpaApp
            val body = ZumpaThreadBody(app.zumpaPrefs.nickName, app.zumpaData[argThreadId]?.subject ?: "", msg, argThreadId)
            val observable = it.sendResponse(argThreadId, argThreadId, body)
            isSending = true
            context.hideKeyboard(view)
            observable
                    .subscribeOn(Schedulers.io())
                    .compose(bindToLifecycle<ZumpaThreadResult>())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(RxTransformers.zumpaRedirectHandler())
                    .subscribe(
                            { result ->
                                //this should be never called
                                hideMessagePanel(true)
                                loadData(SCROLL_DOWN)
                                isSending = false
                            },
                            { err ->
                                err.message?.let { toast(it) }
                                isSending = false
                            }
                    )
        }
    }

    @Subscribe
    fun onLoadThreadEvent(event: LoadThreadEvent) {
        val sameThread = argThreadId == event.id
        if (!sameThread) {
            arguments?.putString(ARG_THREAD_ID, event.id)
        }
        delegate.onLoadThreadEvent(event)
        loadData(event.id, true, if (sameThread) SCROLL_NONE else SCROLL_UP)
    }

    fun loadData(scrollWay: Int = SCROLL_NONE) {
        loadData(argThreadId, false, scrollWay)
    }

    override fun reloadData() {
        loadData(argThreadId, true, SCROLL_DOWN)
        postMessageView?.let {
            if (it.isVisible()) {
                it.hideAnimated()
            }
        }
        mainActivity?.floatingButton?.showAnimated()
    }

    fun loadData(tid: String, force: Boolean = false, scrollWay: Int = SCROLL_NONE) {
        if ((isLoading && !force) || tid.isNullOrEmpty()) {
            isSending = false
            return
        }
        val context = requireActivity()
        isLoading = true
        zumpaApp.zumpaAPI.getThreadPage(tid, tid).let {
            it.subscribeOn(Schedulers.io())
                    .compose(bindToLifecycle<ZumpaThreadResult>())
                    .observeOn(AndroidSchedulers.mainThread())
                    .map {
                        it.items.forEach { item ->
                            item.styledAuthor(context)
                            item.styledBody(context)
                        }
                        it
                    }
                    .retry(3)
                    .subscribe(
                            { result ->
                                val rv = recyclerView!!
                                val offsetY = -2 * rv.computeVerticalScrollOffset()
                                onResultLoaded(result, force)
                                val scrollWayValue = if (argScrollDown && firstLoad) SCROLL_DOWN else scrollWay
                                if (scrollWayValue != 0) {
                                    firstLoad = false
                                    when (scrollWayValue) {
                                        SCROLL_UP -> rv.smoothScrollBy(0, offsetY)
                                        SCROLL_DOWN -> rv.smoothScrollToPosition(rv.adapter?.itemCount ?: 0)
                                    }
                                }
                                isSending = false
                                isLoading = false
                            },
                            { err ->
                                err?.message?.let { toast(it) }
                                isSending = false
                                isLoading = false
                            }
                    )
        }
    }


    override fun onFloatingButtonClick() {
        showMessagePanel()
    }


    override fun onBackButtonClick(): Boolean {
        if (isLoggedIn) {
            if (hideMessagePanel()) {
                return true
            }
        }
        return super.onBackButtonClick()
    }

    fun showMessagePanel() {
        postMessageView?.let {
            if (!it.isVisible()) {
                it.showAnimated()
            }
            mainActivity?.floatingButton?.hideAnimated()
        }
    }

    fun hideMessagePanel(clearText: Boolean = false): Boolean {
        if (clearText) {
            postMessageView?.message?.text = null
        }
        val result = delegate.hideMessagePanel()
        return result ?: false
    }

    private fun onResultLoaded(result: ZumpaThreadResult, clearData: Boolean) {
        result.items.let {
            var items = it
            storeReadState(result)
            recyclerView?.let {
                val loadImages = zumpaApp.zumpaPrefs.loadImages ?: true
                if (it.adapter == null) {
                    recyclerView?.adapter = SubListAdapter(items, loadImages).apply {
                        itemClickListener = this@SubListFragment
                        surveyClickListner = this@SubListFragment
                    }
                } else {
                    (recyclerView?.adapter as SubListAdapter).apply {
                        this.loadImages = loadImages
                        updateItems(items, clearData)
                    }
                }
            }
        }
    }

    private fun storeReadState(result: ZumpaThreadResult) {
        val zumpaReadStates = zumpaApp.zumpaReadStates
        zumpaReadStates?.let {
            val size = result.items.size - 1
            if (it.containsKey(argThreadId)) {
                it[argThreadId]!!.count = size//don't count 1st one as it's actual post
            } else {
                it[argThreadId] = ZumpaReadState(argThreadId, size)
            }
        }
    }

    override fun onMenuItemClick(position: Int, item: ZumpaThreadItem, type: Int) {
        when(type) {
            tReply -> {
                postMessageView
                        ?.takeIf { zumpaApp.zumpaPrefs.isLoggedInNotOffline }
                        ?.let {
                            showMessagePanel()
                            it.message.text.apply {
                                val text = "@${item.authorReal}: \n"
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
            SubListAdapter.tCopy -> {
                (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).apply {
                    text = item.body
                    toast(R.string.saved_into_clipboard)
                }
            }
            SubListAdapter.tSpeak -> {
                postMessageView
                        ?.takeIf { zumpaApp.zumpaPrefs.isLoggedInNotOffline }
                        ?.let {
                            showMessagePanel()
                            val result = it.message.text
                            if (result.isNotEmpty()) {
                                result.append("\n")
                            }
                            result.append("${item.author}: ${item.body}\n----\n")
                            it.message.text = result
                            it.message.setSelection(it.message.length())
                        }
            }
        }
        (recyclerView?.adapter as? ToggleAdapter)?.closeMenu(position)
    }

    override fun onItemClick(position: Int, item: ZumpaThreadItem, longClick: Boolean, view: View) {
        if (postMessageView != null && zumpaApp.zumpaPrefs.isLoggedInNotOffline ?: false) {
            delegate.onItemClick(position, item, longClick)
        }
    }

    override fun onItemClick(item: SurveyItem) {
        if (zumpaApp.zumpaPrefs.isLoggedInNotOffline) {
            zumpaApp.zumpaAPI.voteSurvey(ZumpaVoteSurveyBody(item.surveyId, item.id)).let {
                isSending = true
                it.subscribeOn(Schedulers.io())
                        .compose(bindToLifecycle<ZumpaGenericResponse>())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { result -> loadData() },
                                { err ->
                                    err?.message?.let { toast(it) }
                                    isSending = false
                                }
                        )
            }
        }
    }

    override fun onItemClick(url: String, longClick: Boolean, view: View) {
        val context = requireContext()
        if (longClick) {
            context.saveToClipboard(Uri.parse(url))
            context.toast(R.string.saved_into_clipboard)
        } else {
            val id = ZumpaSimpleParser.getZumpaThreadId(url)
            if (id != 0) {
                delegate.onThreadLinkClick(id)
            } else {
                val activity = requireActivity()
                if (url.isImageUri()) {
                    val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, view, getString(R.string.transition_image)).toBundle()
                    startActivity(ImageActivity.createIntent(activity, url), bundle)
                } else {
                    context.startLinkActivity(url)
                }
            }
        }
    }

    private abstract class BehaviourDelegate(val fragment: SubListFragment) {
        open fun onThreadLinkClick(threadId: Int) {}
        open fun onItemClick(position: Int, item: ZumpaThreadItem, longClick: Boolean) {}
        open fun hideMessagePanel(): Boolean? {
            return null
        }

        open fun onResume() {}
        open fun onViewCreated() {}
        open fun onLoadThreadEvent(event: LoadThreadEvent) {}
        open fun openPostFragment(flag : Int?) {}
    }

    private class PhoneBehaviour(fragment: SubListFragment) : BehaviourDelegate(fragment) {
        override fun onViewCreated() {
            fragment.postMessageView?.visibility = View.INVISIBLE
        }

        override fun onResume() {
            if (fragment.isLoggedIn) {
                if (fragment.postMessageView?.isVisible() == true) {
                    fragment.mainActivity?.floatingButton?.hideAnimated()
                } else {
                    fragment.mainActivity?.floatingButton?.showAnimated()
                    hideMessagePanel()
                }
            }
        }

        override fun hideMessagePanel(): Boolean? {
            fragment.postMessageView?.let {
                if (it.isVisible()) {
                    it.hideAnimated()
                    fragment.mainActivity?.floatingButton?.showAnimated()
                    return true
                }
            }
            return null
        }

        override fun onItemClick(position: Int, item: ZumpaThreadItem, longClick: Boolean) {
            if (longClick) {
                (fragment.recyclerView?.adapter as? ToggleAdapter)?.toggleOpenState(position)
            } else if (fragment.postMessageView?.isVisible() == true) {
                fragment.onMenuItemClick(position, item, tReply)
            }
        }

        override fun onThreadLinkClick(threadId: Int) {
            fragment.openFragment(newInstance(threadId.toString()), true, true)
        }

        override fun openPostFragment(flag : Int?) {
            val f = if (flag == null) {
                PostFragment()
            } else {
                PostFragment
                        .newInstance(fragment.title.toString(), fragment.postMessageView!!.message.text.toString(), null, fragment.argThreadId, flag)

            }
            fragment.openFragment(f, true, false)
        }
    }

    private class TabletBehaviour(fragment: SubListFragment) : BehaviourDelegate(fragment) {
        override fun onViewCreated() {
            fragment.postMessageView?.visibility = View.INVISIBLE
        }

        override fun onThreadLinkClick(threadId: Int) {
            fragment.onLoadThreadEvent(LoadThreadEvent(threadId.toString()))
        }

        @SuppressLint("RestrictedApi")
        override fun onLoadThreadEvent(event: LoadThreadEvent) {
            fragment.postMessageView?.visibility = View.VISIBLE
            fragment.mainActivity?.floatingButton?.visibility = View.GONE
        }

        override fun openPostFragment(flag: Int?) {
            val f = if (flag == null) {
                PostFragment()
            } else {
                PostFragment
                        .newInstance(fragment.title.toString(), fragment.postMessageView!!.message.text.toString(), null, fragment.argThreadId, flag)

            }
            f.show(fragment.childFragmentManager, "PostFragment")
        }
    }
}
