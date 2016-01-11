package com.scurab.android.zumpareader.content.post

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.v4.app.FragmentTabHost
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TabWidget
import com.pawegio.kandroid.find
import com.pawegio.kandroid.layoutInflater
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.*

/**
 * Created by JBruchanov on 08/01/2016.
 */
public class PostFragment : BaseFragment() {
    companion object {

        public fun newInstance(subject: String?, message: String?, uris: Array<Uri>? = null): PostFragment {
            return PostFragment().apply {
                arguments = arguments(subject, message, uris)
            }
        }

        public fun arguments(subject: String?, message: String?, uris: Array<Uri>? = null): Bundle {
            return Bundle().apply {
                putString(Intent.EXTRA_SUBJECT, subject)
                putString(Intent.EXTRA_TEXT, message)
                putParcelableArray(Intent.EXTRA_STREAM, uris)
            }
        }

    }

    val tabHost : FragmentTabHost? get() { return view.find<FragmentTabHost>(android.R.id.tabhost) }
    val contextColor by lazy { context.obtainStyledColor(R.attr.contextColor)}
    override val title: CharSequence?
        get() = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_post, container, false)
        val tabHost = view.find<FragmentTabHost>(android.R.id.tabhost)
        val tabWidget = view.find<TabWidget>(android.R.id.tabs)
        tabHost.execOn {
            setup(context, childFragmentManager, android.R.id.tabcontent)
            addTab(newTabSpec("1").setIndicator(createIndicator(R.drawable.ic_pen, contextColor, tabWidget)), PostMessageFragment::class.java, PostMessageFragment.arguments(argSubject, argMessage, argUris == null))
            if (argUris != null) {
                var i = 1
                val uris = argUris
                for (argUri in uris!!.asIterable()) {
                    addTab(newTabSpec((++i).toString()).setIndicator(createIndicator(R.drawable.ic_photo, contextColor, tabWidget)), PostImageFragment::class.java, PostImageFragment.arguments(argUri))
                }
                if (i == 2) {//just single image
                    post { setCurrentTabByTag(i.toString()) }
                }
            }
            setOnTabChangedListener { context.hideKeyboard(view) }
        }
        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val isImage = (PostMessageFragment.REQ_CODE_IMAGE == requestCode || PostMessageFragment.REQ_CODE_CAMERA == requestCode)
        if (isImage && resultCode == Activity.RESULT_OK) {
            try {
                val uri: String
                val icon: Int
                if (PostMessageFragment.REQ_CODE_CAMERA == requestCode) {
                    uri = zumpaApp!!.zumpaPrefs.lastCameraUri
                    icon = R.drawable.ic_camera
                } else {
                    uri = data!!.dataString
                    icon = R.drawable.ic_photo
                }
                tabHost.execOn {
                    var newIndex = (childFragmentManager.fragments.size + 1).toString()
                    addTab(newTabSpec(newIndex).setIndicator(createIndicator(icon, contextColor, tabWidget)), PostImageFragment::class.java, PostImageFragment.arguments(Uri.parse(uri)))
                    post { setCurrentTabByTag(newIndex) }
                }
            } catch(e: Throwable) {
                context.toast(e.message)
            }
        }
    }

    private fun createIndicator(@DrawableRes resId: Int, @ColorInt color: Int, parent: ViewGroup?): View {
        val btn = context.layoutInflater.inflate(R.layout.view_tab_button, parent, false) as ImageView
        val res = context.resources
        val icon = res.getDrawable(resId).wrapWithTint(color)
        btn.setImageDrawable(icon)
        return btn
    }

    override fun onResume() {
        super.onResume()
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

    private val argUris: Array<Uri>? by lazy {
        var result: Array<Uri>? = null
        if (arguments != null && arguments.containsKey(Intent.EXTRA_STREAM)) {
            result = arguments.getParcelableArray(Intent.EXTRA_STREAM) as Array<Uri>?
        }
        result
    }

    override fun onDestroyView() {
        (activity as? MainActivity).exec {
            it.floatingButton.showAnimated()
        }
        super.onDestroyView()
    }
}