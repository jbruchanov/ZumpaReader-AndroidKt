package com.scurab.android.zumpareader.content

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import com.scurab.android.zumpareader.BusProvider
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.app.SettingsActivity
import com.scurab.android.zumpareader.content.post.PostFragment
import com.scurab.android.zumpareader.event.DialogEvent
import com.scurab.android.zumpareader.event.LoadThreadEvent
import com.scurab.android.zumpareader.model.ZumpaGenericResponse
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaToggleBody
import com.scurab.android.zumpareader.ui.hideAnimated
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.*
import com.squareup.otto.Subscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.find
import org.jetbrains.anko.support.v4.toast

/**
 * Created by JBruchanov on 24/11/2015.
 */
open class MainListFragment : BaseFragment(), MainListAdapter.OnShowItemListener, IsReloadable {

    private var content: View? = null
    private val recyclerView: RecyclerView get() = content!!.find<RecyclerView>(R.id.recycler_view)
    private val swipeToRefresh: SwipyRefreshLayout get() = content!!.find<SwipyRefreshLayout>(R.id.swipe_refresh_layout)
    private var lastFilter: String = ""
    private var lastOffline: Boolean? = null
    private var invalidateOptionsMenu = false

    private var nextThreadId: String? = null
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

    override val title: CharSequence get() {
        val appName = getString(R.string.app_name)
        return if (zumpaApp?.zumpaPrefs?.isOffline ?: false) {
            "%s (%s)".format(appName, getString(R.string.offline))
        } else {
            appName
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    @Subscribe fun onDialogEvent(dialogEvent: DialogEvent) {
        onRefreshTitle()
        if (zumpaApp?.zumpaPrefs?.isOffline ?: false) {
            lastOffline = null
            zumpaApp?.loadOfflineData()
            loadPage()
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        content.execIfNull {
            content = inflater!!.inflate(R.layout.view_recycler_refreshable, container, false)
            content.exec {
                swipeToRefresh.direction = SwipyRefreshLayoutDirection.TOP
                swipeToRefresh.setColorSchemeColors(context.getColorFromTheme(R.attr.contextColor))
                recyclerView.apply {
                    layoutManager = LinearLayoutManager(inflater.context, LinearLayoutManager.VERTICAL, false)
                }
            }
        }

        return content
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
            loadPage(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(context, SettingsActivity::class.java))
                invalidateOptionsMenu = true
                return true
            }
            R.id.offline -> {
                var app = zumpaApp!!
                if (!app.zumpaPrefs.isOffline) {
                    app.zumpaPrefs.isOffline = !app.zumpaPrefs.isOffline
                    OfflineDownloadFragment().show(mainActivity!!.supportFragmentManager, OfflineDownloadFragment::class.java.name)
                    lastOffline = null
                } else {
                    app.zumpaPrefs.isOffline = !app.zumpaPrefs.isOffline
                    reloadData()
                }
                onRefreshTitle()
                mainActivity?.apply {
                    invalidateOptionsMenu()
                    if (app.zumpaPrefs.isOffline) {
                        floatingButton.hideAnimated()
                    } else {
                        floatingButton.showAnimated()
                    }
                }
                return true
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.offline).apply {
            setTitle(if (zumpaApp!!.zumpaPrefs.isOffline) R.string.online else R.string.offline)
        }
    }

    override fun reloadData() {
        loadPage()
    }

    private fun loadPage(firstLoad:Boolean = false, fromThread: String? = null) {
        if (isLoading || fromThread?.isEmpty() ?: false) {
            return
        }
        if (zumpaApp != null) {
            var zumpaApp = this.zumpaApp!!
            var filter = zumpaApp.zumpaPrefs.filter
            var offline = zumpaApp.zumpaPrefs.isOffline
            if (lastFilter != filter || lastOffline != offline) {
                recyclerView.adapter.exec {
                    (it as MainListAdapter).removeAll()
                }
            }
            lastOffline = offline
            lastFilter = filter
            isLoading = true

            val mainPage = if (fromThread != null) zumpaApp.zumpaAPI.getMainPage(fromThread, filter) else zumpaApp.zumpaAPI.getMainPage(filter)
            mainPage.subscribeOn(Schedulers.io())
                    .compose(bindToLifecycle<ZumpaMainPageResult>())
                    .observeOn(AndroidSchedulers.mainThread())
                    .retry(3)
                    .subscribe(
                            { result ->
                                onResultLoaded(result, firstLoad)
                                isLoading = false
                            },
                            { err ->
                                isLoading = false
                                err?.message?.exec { toast(it) }
                            }
                    )
        }
    }

    override fun onStart() {
        super.onStart()
        if (invalidateOptionsMenu) {
            invalidateOptionsMenu = false
            mainActivity!!.invalidateOptionsMenu()
        }
    }

    override fun onPause() {
        super.onPause()
        isLoading = false
    }

    protected fun onResultLoaded(response: ZumpaMainPageResult?, firstLoad: Boolean) {
        response?.exec {
            zumpaData.putAll(it.items)
            nextThreadId = it.nextThreadId
            val values = it.items.asListOfValues()
            recyclerView.exec {
                var user = zumpaApp?.zumpaPrefs?.loggedUserName
                zumpaApp?.zumpaReadStates.exec {
                    for (zumpaThread in values) {
                        zumpaThread.setStateBasedOnReadValue(it[zumpaThread.id]?.count, user)
                    }
                }
                if (it.adapter != null) {
                    (it.adapter as MainListAdapter).addItems(values)
                } else {
                    val mainListAdapter = MainListAdapter(values)
                    mainListAdapter.setOnShowItemListener(this@MainListFragment, 15)
                    mainListAdapter.onItemClickListener = object : MainListAdapter.OnItemClickListener {
                        override fun onItemClick(item: ZumpaThread, position: Int, type: Int) {
                            when(type) {
                                MainListAdapter.tThread -> onThreadItemClick(item, position)
                                MainListAdapter.tThreadLongClick -> onThreadItemLongClick(item, position)
                                MainListAdapter.tFavorite -> onThreadFavoriteClick(item, position)
                                MainListAdapter.tIgnore -> onThreadIgnoreClick(item, position)
                            }
                        }
                    }
                    it.adapter = mainListAdapter
                }
                if (isTablet && firstLoad) {
                    onThreadItemClick(zumpaData.lastEntry().value, 0)
                }
            }
            isLoading = false
        }
    }

    private fun onThreadItemLongClick(item: ZumpaThread, position: Int) {
        val prefs = zumpaApp?.zumpaPrefs
        if (prefs?.isOffline == false && prefs?.isLoggedIn == true) {
            (recyclerView?.adapter as? MainListAdapter)?.toggleOpenState(position)
        }
    }

    private fun mainListAdapter() = (recyclerView?.adapter as? MainListAdapter)

    open fun onThreadItemClick(item: ZumpaThread, position: Int) {
        isLoading = false
        val oldState = item.state
        item.setStateBasedOnReadValue(item.items, zumpaApp?.zumpaPrefs?.loggedUserName)
        if (oldState != item.state || isTablet) {
            mainListAdapter().exec {
                if (isTablet) {
                    it.setSelectedItem(item, position)
                } else {
                    it.notifyItemChanged(position)
                }
            }
        }

        if (isTablet) {
            BusProvider.post(LoadThreadEvent(item.id))
        } else {
            openFragment(SubListFragment.newInstance(item.id), true, true)
        }
    }

    private fun onThreadIgnoreClick(item: ZumpaThread, position: Int) {
        mainListAdapter()?.let {
            it.toggleOpenState(position)
            zumpaApp?.zumpaAPI?.let {
                isLoading = true
                it.toggleRate(ZumpaToggleBody(item.id, ZumpaToggleBody.tIgnore))
                        .compose(bindToLifecycle<ZumpaGenericResponse>())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { result ->
                                    isLoading = false
                                    zumpaData.remove(item.id)
                                    mainListAdapter()?.removeItem(item)
                                },
                                { err ->
                                    isLoading = false
                                    err?.message?.exec { toast(it) }
                                }
                        )
            }
        }
    }

    private fun onThreadFavoriteClick(item: ZumpaThread, position: Int) {
        mainListAdapter()?.let {
            it.toggleOpenState(position)
            zumpaApp?.zumpaAPI?.let {
                isLoading = true
                it.toggleRate(ZumpaToggleBody(item.id, ZumpaToggleBody.tFavorite))
                        .compose(bindToLifecycle<ZumpaGenericResponse>())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { result ->
                                    isLoading = false
                                    item.isFavorite = !item.isFavorite
                                    mainListAdapter()?.notifyItemChanged(position)
                                },
                                { err ->
                                    isLoading = false
                                    err?.message?.exec { toast(it) }
                                }
                        )
            }
        }
    }

    override fun onShowingItem(source: MainListAdapter, item: Int) {
        if (!isLoading) {
            (recyclerView?.adapter as MainListAdapter).exec {
                loadPage(false, nextThreadId)
            }

        }
    }

    override fun onFloatingButtonClick() {
        activity?.supportFragmentManager.exec {
            openFragment(PostFragment(), !isTablet, false)
            mainActivity?.floatingButton?.hideAnimated()
        }
    }
}