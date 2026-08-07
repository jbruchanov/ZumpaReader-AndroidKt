package com.scurab.android.zumpareader.ui

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ui.main.MainActivity

/**
 * Created by JBruchanov on 25/11/2015.
 */
abstract class BaseDialogFragment : DialogFragment() {

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

}
