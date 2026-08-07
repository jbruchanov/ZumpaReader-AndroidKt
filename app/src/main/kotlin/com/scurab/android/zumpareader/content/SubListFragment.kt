package com.scurab.android.zumpareader.content

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.app.ActivityOptionsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.app.ImageActivity
import com.scurab.android.zumpareader.arch.CopyToClipboard
import com.scurab.android.zumpareader.arch.HideKeyboard
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.content.post.PostFragment
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.text.AuthorSpan
import com.scurab.android.zumpareader.text.SpannedTextRenderer
import com.scurab.android.zumpareader.ui.SendingDialogController
import com.scurab.android.zumpareader.ui.hideAnimated
import com.scurab.android.zumpareader.ui.isVisible
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.getColorFromTheme
import com.scurab.android.zumpareader.util.hideKeyboard
import com.scurab.android.zumpareader.util.looksLikeImageUrl
import com.scurab.android.zumpareader.util.obtainStyledColor
import com.scurab.android.zumpareader.util.removeGlobalLayoutListenerSafe
import com.scurab.android.zumpareader.util.saveToClipboard
import com.scurab.android.zumpareader.util.startLinkActivity
import com.scurab.android.zumpareader.widget.PostMessageView
import com.scurab.android.zumpareader.widget.SurveyView
import com.scurab.android.zumpareader.widget.ToggleAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 27/11/2015.
 */
class SubListFragment : BaseFragment(), SubListAdapter.ItemClickListener,
    SurveyView.ItemClickListener, IsReloadable {

    companion object {
        private const val ARG_THREAD_ID: String = "ARG_THREAD_ID"
        private const val ARG_SCROLL_DOWN: String = "ARG_SCROLL_DOWN"

        fun newInstance(threadId: String, scrollDown: Boolean = false): SubListFragment {
            return SubListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_THREAD_ID, threadId)
                    putBoolean(ARG_SCROLL_DOWN, scrollDown)
                }
            }
        }
    }

    private val viewModel: SubListViewModel by viewModel()
    private val render by lazy { SubListRender(SpannedTextRenderer(requireContext())) }
    private val listAdapter = SubListAdapter()

    private val argThreadId: String get() = arguments?.getString(ARG_THREAD_ID) ?: ""

    private val recyclerView: RecyclerView? get() = view?.findViewById(R.id.recycler_view)
    private val swipyRefreshLayout: SwipyRefreshLayout? get() = view?.findViewById(R.id.swipe_refresh_layout)
    private val postMessageView: PostMessageView? get() = view?.findViewById(R.id.response_panel)
    private val contextColorText: Int by lazy { requireContext().obtainStyledColor(R.attr.contextColorText2) }
    private val treeViewObserver = ViewTreeObserver.OnGlobalLayoutListener { updateRecycleViewPadding() }
    private val sendingDialog by lazy { SendingDialogController(requireContext()) }

    private var renderedTitle: CharSequence = ""
    private var isSettingDraft = false

    override val title: CharSequence get() = renderedTitle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val content = inflater.inflate(R.layout.view_recycler_refreshable_thread, container, false)
        content.setBackgroundColor(Color.BLACK)
        return content
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView?.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        recyclerView?.adapter = listAdapter
        listAdapter.itemClickListener = this
        listAdapter.surveyClickListner = this

        swipyRefreshLayout?.apply {
            direction = SwipyRefreshLayoutDirection.BOTTOM
            setOnRefreshListener { viewModel.onRefresh() }
            setColorSchemeColors(context.getColorFromTheme(R.attr.contextColor))
        }
        postMessageView?.apply {
            visibility = View.INVISIBLE
            addButton.visibility = isTabletVisibility
            addButton.setOnClickListener { viewModel.onOpenPostFragment() }
            sendButton.setOnClickListener { viewModel.onSend() }
            camera.setOnClickListener { viewModel.onOpenPostFragment(R.id.camera) }
            photo.setOnClickListener { viewModel.onOpenPostFragment(R.id.photo) }
            message.addTextChangedListener(draftWatcher)
        }

        collectState()
        viewModel.start(argThreadId)
    }

    private fun collectState() {
        viewModel.uiState
            .map { it.rows }
            .distinctUntilChanged()
            .map { render.rows(it) }
            .flowOn(Dispatchers.Default)
            .collectWhileStarted { listAdapter.setItems(it) }

        viewModel.uiState
            .map { it.title }
            .distinctUntilChanged()
            .map { if (it.isEmpty()) "" else render.titleOf(it) }
            .flowOn(Dispatchers.Default)
            .collectWhileStarted {
                renderedTitle = it.ifEmpty { getString(R.string.app_name) }
                onRefreshTitle()
            }

        viewModel.uiState
            .map { it.isLoading }
            .distinctUntilChanged()
            .collectWhileStarted { isLoading ->
                progressBarVisible = isLoading
                swipyRefreshLayout?.let {
                    if (it.isRefreshing != isLoading) {
                        it.isRefreshing = isLoading
                    }
                }
            }

        viewModel.uiState
            .map { it.isSending }
            .distinctUntilChanged()
            .collectWhileStarted { sendingDialog.update(it) }

        viewModel.uiState
            .map { it.isPostPanelVisible }
            .distinctUntilChanged()
            .collectWhileStarted { renderPostPanel(it) }

        viewModel.uiState
            .map { it.draft }
            .distinctUntilChanged()
            .collectWhileStarted { renderDraft(it) }

        viewModel.effects.collectWhileStarted { onEffect(it) }
    }

    private fun renderPostPanel(isVisible: Boolean) {
        val panel = postMessageView ?: return
        if (isVisible && !panel.isVisible()) {
            panel.showAnimated()
            mainActivity?.hideFloatingButton()
        } else if (!isVisible && panel.isVisible()) {
            panel.hideAnimated()
            mainActivity?.showFloatingButton()
        }
    }

    /**
     * Only writes when the model and the widget actually disagree - otherwise every keystroke would
     * come back as a setText and fight the cursor. The reply headers get their colour back here,
     * which is the styling half of what AuthorSpan used to do.
     */
    private fun renderDraft(draft: DraftUiState) {
        val editText = postMessageView?.message ?: return
        if (editText.text.toString() == draft.text) {
            return
        }
        isSettingDraft = true
        editText.setText(draft.text)
        var start = 0
        draft.headers.forEach { header ->
            editText.text.setSpan(
                AuthorSpan(contextColorText),
                start,
                start + header.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            start += header.length
        }
        editText.setSelection(editText.length())
        isSettingDraft = false
    }

    private val draftWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (!isSettingDraft) {
                viewModel.onDraftChanged(s?.toString() ?: "")
            }
        }
    }

    private fun onEffect(effect: UiEffect) {
        when (effect) {
            is SubListEffect.ScrollToBottom ->
                recyclerView?.let { it.smoothScrollToPosition(it.adapter?.itemCount ?: 0) }

            is SubListEffect.ScrollToTop ->
                recyclerView?.let { it.smoothScrollBy(0, -2 * it.computeVerticalScrollOffset()) }

            is SubListEffect.OpenThread ->
                openFragment(newInstance(effect.threadId), true, true)

            is SubListEffect.OpenPostFragment -> openPostFragment(effect.flag)
            is CopyToClipboard -> {
                requireContext().saveToClipboard(effect.text.toString())
                toast(R.string.saved_into_clipboard)
            }

            is HideKeyboard -> requireContext().hideKeyboard(view)
            is ShowToast -> effect.text?.let { toast(it) } ?: toast(effect.resId)
            else -> Unit
        }
    }

    private fun openPostFragment(flag: Int?) {
        val fragment = if (flag == null) {
            PostFragment()
        } else {
            PostFragment.newInstance(
                title.toString(),
                viewModel.uiState.value.draft.text,
                null,
                viewModel.uiState.value.threadId,
                flag
            )
        }
        if (isTablet) {
            fragment.show(childFragmentManager, "PostFragment")
        } else {
            openFragment(fragment, true, false)
        }
    }

    override fun onResume() {
        super.onResume()
        mainActivity?.setScrollStrategyEnabled(false)
        requireView().viewTreeObserver.addOnGlobalLayoutListener(treeViewObserver)
    }

    override fun onPause() {
        mainActivity?.setScrollStrategyEnabled(true)
        requireView().viewTreeObserver.removeGlobalLayoutListenerSafe(treeViewObserver)
        super.onPause()
    }

    override fun onDestroyView() {
        sendingDialog.update(false)
        postMessageView?.message?.removeTextChangedListener(draftWatcher)
        super.onDestroyView()
    }

    private fun updateRecycleViewPadding() {
        if (viewModel.uiState.value.canPost) {
            requireView().post {
                recyclerView?.apply {
                    setPadding(paddingLeft, paddingTop, paddingRight, postMessageView?.height ?: 0)
                }
            }
        }
    }

    override fun onFloatingButtonClick() = viewModel.showPostPanel()

    override fun onBackButtonClick(): Boolean = viewModel.onBackPressed() || super.onBackButtonClick()

    override fun reloadData() = viewModel.reload()

    //region adapter callbacks
    override fun onMenuItemClick(position: Int, item: RenderedSubListRow.Message, type: Int) {
        when (type) {
            SubListAdapter.tReply -> viewModel.onReplyClick(item.rawAuthorReal)
            SubListAdapter.tCopy -> viewModel.onCopyClick(item.rawBody)
            SubListAdapter.tSpeak -> viewModel.onQuoteClick(item.rawAuthor, item.rawBody)
        }
        (recyclerView?.adapter as? ToggleAdapter)?.closeMenu(position)
    }

    override fun onItemClick(position: Int, item: RenderedSubListRow.Message, longClick: Boolean, view: View) {
        if (!viewModel.uiState.value.canPost) {
            return
        }
        if (longClick) {
            (recyclerView?.adapter as? ToggleAdapter)?.toggleOpenState(position)
        } else if (postMessageView?.isVisible() == true) {
            viewModel.onReplyClick(item.rawAuthorReal)
        }
    }

    override fun onItemClick(item: SurveyItemUiState) = viewModel.onSurveyVote(item)

    /**
     * Link routing stays here - it is navigation, and the image case needs the tapped view for the
     * shared element transition.
     */
    override fun onItemClick(url: String, longClick: Boolean, view: View) {
        val context = requireContext()
        if (longClick) {
            context.saveToClipboard(Uri.parse(url))
            toast(R.string.saved_into_clipboard)
            return
        }
        val threadId = ZumpaSimpleParser.getZumpaThreadId(url)
        when {
            threadId != 0 -> viewModel.onThreadLinkClick(threadId.toString())
            url.looksLikeImageUrl() -> {
                val activity = requireActivity()
                val bundle = ActivityOptionsCompat
                    .makeSceneTransitionAnimation(activity, view, getString(R.string.transition_image))
                    .toBundle()
                startActivity(ImageActivity.createIntent(activity, url), bundle)
            }

            else -> context.startLinkActivity(url)
        }
    }
    //endregion
}
