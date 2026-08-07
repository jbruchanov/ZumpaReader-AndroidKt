package com.scurab.android.zumpareader.ui

import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.collectWhileStarted
import com.scurab.android.zumpareader.ui.main.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * Created by JBruchanov on 25/11/2015.
 *
 * What is left after the mvvm migration: the handle on the host chrome and a collector bound to
 * the view lifecycle. No shared data, no loading flag, no bus.
 */
abstract class BaseFragment : Fragment() {

    /** Collects against the *view* lifecycle - only valid between onViewCreated and onDestroyView. */
    protected fun <T> Flow<T>.collectWhileStarted(block: suspend (T) -> Unit): Job =
        collectWhileStarted(viewLifecycleOwner, block)

    val mainActivity: MainActivity?
        get() {
            return activity as MainActivity?
        }

    /** The toolbar spinner, owned by the host and driven by each screen's own loading state. */
    var progressBarVisible: Boolean
        get() {
            return mainActivity?.progressBarVisible ?: false
        }
        set(value) {
            mainActivity?.progressBarVisible = value
        }

    protected abstract val title: CharSequence?

    private var _isTablet: Boolean? = null
    protected val isTablet: Boolean
        get() {
            if (_isTablet == null) {
                _isTablet = resources.getBoolean(R.bool.is_tablet)
            }
            return _isTablet!!
        }

    protected val isTabletVisibility: Int
        get() {
            return if (isTablet) View.VISIBLE else View.INVISIBLE
        }

    open fun onMenuItemClick(item: MenuItem): Boolean {
        return false
    }

    open fun openFragment(fragment: Fragment, addToBackStack: Boolean = true, replace: Boolean = true) {
        mainActivity?.openFragment(fragment, addToBackStack, replace)
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
}
