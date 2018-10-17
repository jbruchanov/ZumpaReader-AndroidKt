package com.scurab.android.zumpareader.app

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.MenuItem
import android.view.View
import com.scurab.android.zumpareader.BusProvider
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.extension.app
import com.scurab.android.zumpareader.model.ZumpaThread
import com.trello.rxlifecycle2.components.support.RxFragment
import java.util.*

/**
 * Created by JBruchanov on 25/11/2015.
 */
abstract class BaseFragment : RxFragment() {

    val mainActivity: MainActivity?
        get() {
            return activity as MainActivity?
        }

    val zumpaApp: ZumpaReaderApp get() = app()

    var progressBarVisible: Boolean
        get() {
            return mainActivity?.progressBarVisible ?: false
        }
        set(value) {
            mainActivity?.progressBarVisible = value
        }

    private var _isLoading: Boolean = false
    protected open var isLoading: Boolean
        get() {
            return _isLoading
        }
        set(value) {
            progressBarVisible = value
            _isLoading = value
        }

    private var _zumpaData: TreeMap<String, ZumpaThread>? = null
    protected val zumpaData: TreeMap<String, ZumpaThread> by lazy { _zumpaData!! }
    protected abstract val title: CharSequence?

    private var _isTablet: Boolean? = null
    protected val isTablet: Boolean
        get() {
            if (_isTablet == null) {
                _isTablet = resources.getBoolean(R.bool.is_tablet)
            }
            return _isTablet!!
        }
    protected val isTabletVisibility:Int
        get() {
            return if (isTablet) View.VISIBLE else View.INVISIBLE
        }

    open fun onMenuItemClick(item: MenuItem): Boolean {
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BusProvider.register(this)
    }

    override fun onDestroy() {
        BusProvider.unregister(this)
        super.onDestroy()
    }

    open fun openFragment(fragment: Fragment, addToBackStack: Boolean = true, replace: Boolean = true) {
        mainActivity?.openFragment(fragment, addToBackStack, replace)
        isLoading = false
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        _zumpaData = zumpaApp.zumpaData
    }

    override fun onResume() {
        super.onResume()
        onRefreshTitle()
    }

    protected fun onRefreshTitle() {
        if (title != null) {
            mainActivity?.title = title
        }
    }

    open fun onFloatingButtonClick() {

    }

    open fun onBackButtonClick(): Boolean {
        return false
    }

    protected val isLoggedIn: Boolean
        get() {
            return zumpaApp.zumpaPrefs.isLoggedInNotOffline ?: false
        }
}
