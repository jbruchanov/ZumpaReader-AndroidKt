package com.scurab.android.zumpareader.content.post

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.v4.app.FragmentTabHost
import android.support.v4.content.FileProvider
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TabWidget
import com.scurab.android.zumpareader.BuildConfig
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseDialogFragment
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.*
import org.jetbrains.anko.find
import org.jetbrains.anko.layoutInflater
import java.io.File

/**
 * Created by JBruchanov on 08/01/2016.
 */
class PostFragment : BaseDialogFragment() {
    companion object {
        val REQ_CODE_IMAGE = 123
        val REQ_CODE_CAMERA = 124

        private val POST_MESSAGE_TAG = "1"
        val THREAD_ID = "THREAD_UD"
        val FLAG = "FLAG"

        fun newInstance(subject: String?, message: String?, uris: Array<Uri>? = null, threadId: String? = null, flag: Int = 0): PostFragment {
            return PostFragment().apply {
                arguments = arguments(subject, message, uris, threadId, flag)
            }
        }

        fun arguments(subject: String?, message: String?, uris: Array<Uri>? = null, threadId: String? = null, flag: Int = 0): Bundle {
            return Bundle().apply {
                putString(Intent.EXTRA_SUBJECT, subject)
                putString(Intent.EXTRA_TEXT, message)
                putParcelableArray(Intent.EXTRA_STREAM, uris)
                putString(THREAD_ID, threadId)
                putInt(FLAG, flag)
            }
        }

        fun isRequestCode(requestCode: Int): Boolean {
            return REQ_CODE_CAMERA == requestCode || REQ_CODE_IMAGE == requestCode
        }

    }

    val tabHost: FragmentTabHost? get() {
        return view!!.find<FragmentTabHost>(android.R.id.tabhost)
    }
    val contextColor by lazy { context.obtainStyledColor(R.attr.contextColor) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity.execOn {
            hideFloatingButton()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_post, container, false)
        val tabHost = view.find<FragmentTabHost>(android.R.id.tabhost)
        val tabWidget = view.find<TabWidget>(android.R.id.tabs)
        tabHost.execOn {
            setup(context, childFragmentManager, android.R.id.tabcontent)
            addTab(newTabSpec(POST_MESSAGE_TAG).setIndicator(createIndicator(R.drawable.ic_pen, contextColor, tabWidget)), PostMessageFragment::class.java, PostMessageFragment.arguments(argSubject, argMessage, argUris == null, argThreadId))
            if (argUris != null) {
                var i = 1
                val uris = argUris
                for (argUri in uris!!.asIterable()) {
                    addTab(newTabSpec((++i).toString()).setIndicator(createIndicator(R.drawable.ic_photo, contextColor, tabWidget)), PostImageFragment::class.java, PostImageFragment.arguments(argUri))
                }
                if (i == 2) {
                    //just single image
                    post { setCurrentTabByTag(i.toString()) }
                }
            }
            setOnTabChangedListener { context.hideKeyboard(view) }
        }
        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val isImage = (REQ_CODE_IMAGE == requestCode || REQ_CODE_CAMERA == requestCode)
        if (isImage && resultCode == Activity.RESULT_OK) {
            try {
                val uri: String
                val icon: Int
                if (REQ_CODE_CAMERA == requestCode) {
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

    private val argSubject: String? by lazy { arguments?.getString(Intent.EXTRA_SUBJECT) }
    private val argMessage: String? by lazy { arguments?.getString(Intent.EXTRA_TEXT) }
    private val argUris: Array<Uri>? by lazy {
        var result: Array<Uri>? = null
        if (arguments != null && arguments.containsKey(Intent.EXTRA_STREAM)) {
            result = arguments.getParcelableArray(Intent.EXTRA_STREAM) as Array<Uri>?
        }
        result
    }
    private val argThreadId: String? by lazy { arguments?.getString(THREAD_ID) }
    private var argFlagUsed = false//use it just for first time
    private val argFlag: Int by lazy { arguments?.getInt(FLAG) ?: 0 }

    fun onSharedImage(link: String, activateFragment: Boolean = true) {
        tabHost?.currentTab = 0
        (childFragmentManager.findFragmentByTag(POST_MESSAGE_TAG) as? PostMessageFragment).exec {
            it.addLink(link)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!argFlagUsed && argFlag != 0) {
            argFlagUsed = true
            context.post(Runnable {
                when (argFlag) {
                    R.id.photo -> onPhotoClick()
                    R.id.camera -> onCameraClick()
                }
            })
        }
    }

    fun onPhotoClick() {
        try {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(intent, REQ_CODE_IMAGE)
        } catch(e: Exception) {
            context.toast(R.string.err_fail)
        }
    }

    fun onCameraClick() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val cameraFileUri = context.getRandomCameraFileUri()
            val photoURI = FileProvider.getUriForFile(context, BuildConfig.Authority, File(cameraFileUri))
            zumpaApp!!.zumpaPrefs.lastCameraUri = photoURI.toString()
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            startActivityForResult(intent, REQ_CODE_CAMERA)
        } catch(e: Exception) {
            context.toast(R.string.err_fail)
        }
    }

    private fun createIndicator(@DrawableRes resId: Int, @ColorInt color: Int, parent: ViewGroup?): View {
        val btn = context.layoutInflater.inflate(R.layout.view_tab_button, parent, false) as ImageView
        val res = context.resources
        val icon = res.getDrawable(resId).wrapWithTint(color)
        btn.setImageDrawable(icon)
        return btn
    }

    override fun onDestroyView() {
        if (!isTablet && argThreadId == null) {
            (activity as? MainActivity).exec {
                it.floatingButton.showAnimated()
            }
        }
        super.onDestroyView()
    }
}