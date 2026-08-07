package com.scurab.android.zumpareader.ui

import androidx.fragment.app.Fragment
import com.scurab.android.zumpareader.ui.main.MainActivity

/**
 * Created by JBruchanov on 25/11/2015.
 *
 * What is left after the compose migration: a typed handle on the host, for the fragments that
 * still exist to open another one. Deleted with them when nav-compose lands.
 */
abstract class BaseFragment : Fragment() {

    val mainActivity: MainActivity?
        get() = activity as MainActivity?
}
