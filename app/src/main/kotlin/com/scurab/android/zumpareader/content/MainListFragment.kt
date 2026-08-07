package com.scurab.android.zumpareader.content

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.app.SettingsActivity
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.content.post.PostFragment
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.text.SpannedTextRenderer
import com.scurab.android.zumpareader.ui.hideAnimated
import com.scurab.android.zumpareader.util.getColorFromTheme
import com.scurab.android.zumpareader.util.ifNull
import com.scurab.android.zumpareader.widget.ToggleAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 24/11/2015.
 */
open class MainListFragment : BaseFragment(), MainListAdapter.OnShowItemListener, IsReloadable {

    private val viewModel: MainListViewModel by viewModel()

    private var content: View? = null
    private val recyclerView: RecyclerView get() = content!!.findViewById(R.id.recycler_view)
    private val swipeToRefresh: SwipyRefreshLayout get() = content!!.findViewById(R.id.swipe_refresh_layout)

    private val listAdapter = MainListAdapter()
    private val render by lazy { MainListRender(SpannedTextRenderer(requireContext())) }

    private var isOffline = false
    private var returningFromSettings = false

    override val title: CharSequence
        get() {
            val appName = getString(R.string.app_name)
            return if (isOffline) "$appName (${getString(R.string.offline)})" else appName
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        content.ifNull {
            content = inflater.inflate(R.layout.view_recycler_refreshable, container, false)
            swipeToRefresh.direction = SwipyRefreshLayoutDirection.TOP
            swipeToRefresh.setColorSchemeColors(requireContext().getColorFromTheme(R.attr.contextColor))
            recyclerView.layoutManager =
                LinearLayoutManager(inflater.context, RecyclerView.VERTICAL, false)
        }
        return content
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (content?.parent as? ViewGroup)?.removeView(content)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listAdapter.setOnShowItemListener(this, SHOW_ITEM_END_OFFSET)
        listAdapter.onItemClickListener = object : MainListAdapter.OnItemClickListener {
            override fun onItemClick(item: RenderedThreadRow, position: Int, type: Int) {
                when (type) {
                    MainListAdapter.tThread -> viewModel.onThreadClick(item.id)
                    MainListAdapter.tThreadLongClick -> onThreadLongClick(position)
                    MainListAdapter.tFavorite -> onMenuAction(position) { viewModel.onFavoriteClick(item.id) }
                    MainListAdapter.tIgnore -> onMenuAction(position) { viewModel.onIgnoreClick(item.id) }
                    MainListAdapter.tShare -> onMenuAction(position) { viewModel.onShareClick(item.id) }
                }
            }
        }
        recyclerView.adapter = listAdapter
        swipeToRefresh.setOnRefreshListener { viewModel.onRefresh() }

        viewModel.uiState
            .map { it.isLoading to it.isOffline }
            .distinctUntilChanged()
            .collectWhileStarted { (isLoading, offline) -> renderChrome(isLoading, offline) }

        //the span building and the date formatting happen here, off the main thread
        viewModel.uiState
            .map { it.rows }
            .distinctUntilChanged()
            .map { render.rows(it) }
            .flowOn(Dispatchers.Default)
            .collectWhileStarted { listAdapter.setItems(it) }

        viewModel.effects.collectWhileStarted { onEffect(it) }
    }

    private fun renderChrome(isLoading: Boolean, offline: Boolean) {
        progressBarVisible = isLoading
        //guarded, the widget also sets this itself when the user pulls
        if (swipeToRefresh.isRefreshing != isLoading) {
            swipeToRefresh.isRefreshing = isLoading
        }
        if (isOffline != offline) {
            isOffline = offline
            onRefreshTitle()
            mainActivity?.invalidateOptionsMenu()
        }
    }

    private fun onEffect(effect: UiEffect) {
        when (effect) {
            is MainListEffect.OpenThread ->
                openFragment(SubListFragment.newInstance(effect.threadId), true, true)

            is MainListEffect.OpenSettings -> {
                startActivity(Intent(context, SettingsActivity::class.java))
                returningFromSettings = true
            }

            is MainListEffect.OpenPostDialog -> {
                openFragment(PostFragment(), !isTablet, false)
                mainActivity?.floatingButton?.hideAnimated()
            }

            is MainListEffect.ShowOfflineDownloadDialog ->
                mainActivity?.supportFragmentManager?.let {
                    OfflineDownloadFragment().show(it, OfflineDownloadFragment::class.java.name)
                }

            is MainListEffect.ShareThread -> onShare(effect.link)
            is ShowToast -> effect.text?.let { toast(it) } ?: toast(effect.resId)
            else -> Unit
        }
    }

    private fun onShare(link: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, link)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (e: Exception) {
            toast(R.string.unable_to_finish_operation)
        }
    }

    /** The row menu slide-out is a pure view animation, it never was state. */
    private fun onThreadLongClick(position: Int) {
        if (viewModel.uiState.value.canInteract) {
            (recyclerView.adapter as? ToggleAdapter)?.toggleOpenState(position)
        }
    }

    private inline fun onMenuAction(position: Int, action: () -> Unit) {
        (recyclerView.adapter as? ToggleAdapter)?.toggleOpenState(position)
        action()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                viewModel.onSettingsClick()
                true
            }

            R.id.offline -> {
                viewModel.onOfflineToggle()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.offline).setTitle(if (isOffline) R.string.online else R.string.offline)
    }

    override fun reloadData() = viewModel.onRefresh()

    override fun onStart() {
        super.onStart()
        if (returningFromSettings) {
            returningFromSettings = false
            mainActivity?.invalidateOptionsMenu()
        }
    }

    override fun onShowingItem(source: MainListAdapter, item: Int) = viewModel.onLoadMore()

    override fun onFloatingButtonClick() = viewModel.onFabClick()

    private companion object {
        const val SHOW_ITEM_END_OFFSET = 15
    }
}
