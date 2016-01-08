package com.scurab.android.zumpareader.content.post

import android.content.Intent
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.v4.app.FragmentTabHost
import android.support.v7.widget.AppCompatImageButton
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TabWidget
import com.pawegio.kandroid.find
import com.pawegio.kandroid.layoutInflater
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.execOn
import com.scurab.android.zumpareader.util.obtainStyledColor
import com.scurab.android.zumpareader.util.wrapWithTint

/**
 * Created by JBruchanov on 08/01/2016.
 */
public class PostFragment : BaseFragment() {
    companion object {

        public fun newInstance(subject: String?, message: String?): PostFragment {
            return PostFragment().apply {
                arguments = arguments(subject, message)
            }
        }
        public fun arguments(subject: String?, message: String?): Bundle {
            return Bundle().apply {
                putString(Intent.EXTRA_SUBJECT, subject)
                putString(Intent.EXTRA_TEXT, message)
            }
        }

    }

    override val title: CharSequence?
        get() = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_post, container, false)
        val tabHost = view.find<FragmentTabHost>(android.R.id.tabhost)
        val tabWidget = view.find<TabWidget>(android.R.id.tabs)
        tabHost.execOn {
            setup(context, childFragmentManager, android.R.id.tabcontent)
            val contextColor = context.obtainStyledColor(R.attr.contextColor)
            addTab(newTabSpec("1").setIndicator(createIndicator(R.drawable.ic_pen, contextColor, tabWidget)), PostMessageDialog::class.java, arguments(argSubject, argMessage))
        }
        return view
    }

    private fun createIndicator(@DrawableRes resId: Int, @ColorInt color: Int, parent: ViewGroup?): View {
        val btn = context.layoutInflater.inflate(R.layout.view_tab_button, parent, false) as ImageView
        val res = context.resources
        val icon = res.getDrawable(resId).wrapWithTint(color)
        btn.setImageDrawable(icon)
        return btn
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity.execOn {
            hideFloatingButton()
            settingsButton.visibility = View.INVISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainActivity.execOn {
            showFloatingButton()
            settingsButton.visibility = View.VISIBLE
        }
    }

    private val argSubject: String? by lazy {
        if (arguments != null && arguments.containsKey(Intent.EXTRA_SUBJECT)) arguments.getString(Intent.EXTRA_SUBJECT) else null
    }

    private val argMessage: String? by lazy {
        if (arguments != null && arguments.containsKey(Intent.EXTRA_TEXT)) arguments.getString(Intent.EXTRA_TEXT) else null
    }

    override fun onDestroyView() {
        (activity as? MainActivity).exec {
            it.floatingButton.showAnimated()
        }
        super.onDestroyView()
    }
}