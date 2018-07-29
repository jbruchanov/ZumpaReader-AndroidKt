package com.scurab.android.zumpareader.app

import android.os.Bundle
import com.scurab.android.zumpareader.BusProvider
import com.scurab.android.zumpareader.R
import com.trello.rxlifecycle2.components.support.RxDialogFragment

/**
 * Created by JBruchanov on 25/11/2015.
 */
abstract class BaseDialogFragment : RxDialogFragment() {

    val mainActivity: MainActivity?
        get() {
            return activity as MainActivity?
        }

    private var _isTablet: Boolean? = null
    protected val isTablet: Boolean
        get() {
            if (_isTablet == null) {
                _isTablet = resources.getBoolean(R.bool.is_tablet)
            }
            return _isTablet!!
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BusProvider.register(this)
    }

    override fun onDestroy() {
        BusProvider.unregister(this)
        super.onDestroy()
    }
}
