package com.scurab.android.zumpareader.app

import android.content.Context
import android.support.v4.app.Fragment
import android.view.MenuItem
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.model.ZumpaThread
import java.util.*

/**
 * Created by JBruchanov on 25/11/2015.
 */
public abstract class BaseFragment : Fragment() {

    public val mainActivity: MainActivity?
        get() {
            return getActivity() as MainActivity?
        }

    public val zumpaApp: ZumpaReaderApp?
        get() {
            return mainActivity?.zumpaApp
        }

    public var progressBarVisible: Boolean
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
    protected abstract val title: CharSequence

    public open fun onMenuItemClick(item: MenuItem): Boolean {
        return false
    }

    public open fun openFragment(fragment: BaseFragment, addToBackStack: Boolean = true, replace: Boolean = true) {
        mainActivity?.openFragment(fragment, addToBackStack, replace)
        isLoading = false
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        _zumpaData = zumpaApp!!.zumpaData
    }

    override fun onResume() {
        super.onResume()
        mainActivity?.title = title
    }

    public open fun onFloatingButtonClick() {

    }

    public open fun onBackButtonClick(): Boolean {
        return false;
    }
}