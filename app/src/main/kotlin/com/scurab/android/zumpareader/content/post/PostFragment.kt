package com.scurab.android.zumpareader.content.post

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.fragment.app.FragmentTabHost
import androidx.core.content.FileProvider
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TabWidget
import com.scurab.android.zumpareader.BuildConfig
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseDialogFragment
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.extension.app
import com.scurab.android.zumpareader.giphy.GiphyActivity
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.*
import org.jetbrains.anko.find
import org.jetbrains.anko.layoutInflater
import java.io.File

/**
 * Created by JBruchanov on 08/01/2016.
 */

private const val ARG_FLAG_USED = "ARG_FLAG_USED"

class PostFragment : BaseDialogFragment() {
    companion object {
        val REQ_CODE_IMAGE = 123
        val REQ_CODE_CAMERA = 124
        val REQ_CODE_GIPHY = 125

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
            return REQ_CODE_CAMERA == requestCode || REQ_CODE_IMAGE == requestCode || REQ_CODE_GIPHY == requestCode
        }

    }

    val tabHost: FragmentTabHost? get() {
        return view!!.find(android.R.id.tabhost)
    }
    val contextColor by lazy { requireContext().obtainStyledColor(R.attr.contextColor) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity?.let {
            it.post { it.hideFloatingButton() }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        argFlagUsed = savedInstanceState?.getBoolean(ARG_FLAG_USED, false) ?: false
        val view = inflater.inflate(R.layout.fragment_post, container, false)
        val tabHost = view.find<FragmentTabHost>(android.R.id.tabhost)
        val tabWidget = view.find<TabWidget>(android.R.id.tabs)
        tabHost.apply {
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

    private var pendingGiphyLink: String? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        resultCode
                .takeIf { it == Activity.RESULT_OK }
                ?.let {
                    when (requestCode) {
                        REQ_CODE_CAMERA,
                        REQ_CODE_IMAGE -> {
                            try {
                                val uri: String
                                val icon: Int
                                if (REQ_CODE_CAMERA == requestCode) {
                                    uri = app().zumpaPrefs.lastCameraUri
                                    icon = R.drawable.ic_camera
                                } else {
                                    uri = data!!.dataString
                                    icon = R.drawable.ic_photo
                                }
                                tabHost?.apply {
                                    //childFragmentManager.fragments doesn't have tabs anymore :/ i'd guess it's a bug
                                    var newIndex = "%s - %s".format(System.currentTimeMillis(), uri)
                                    addTab(newTabSpec(newIndex).setIndicator(createIndicator(icon, contextColor, tabWidget)), PostImageFragment::class.java, PostImageFragment.arguments(Uri.parse(uri)))
                                    post { setCurrentTabByTag(newIndex) }
                                }
                            } catch (e: Throwable) {
                                requireContext().toast(e.message)
                            }
                        }
                        REQ_CODE_GIPHY -> {
                            pendingGiphyLink = data?.data.toString()
                        }
                        else -> Unit
                    }
                }
    }

    private val argSubject: String? by lazy { arguments?.getString(Intent.EXTRA_SUBJECT) }
    private val argMessage: String? by lazy { arguments?.getString(Intent.EXTRA_TEXT) }
    private val argUris: Array<Uri>? by lazy {
        var result: Array<Uri>? = null
        arguments?.let {
            if (it.containsKey(Intent.EXTRA_STREAM)) {
                result = it.getParcelableArray(Intent.EXTRA_STREAM) as Array<Uri>?
            }
        }
        result
    }
    private val argThreadId: String? by lazy { arguments?.getString(THREAD_ID) }
    //TODO: doesn't work with lifecycle!, has to be saved
    private var argFlagUsed = false//use it just for first time
    private val argFlag: Int by lazy { arguments?.getInt(FLAG) ?: 0 }

    fun onSharedImage(link: String, activateFragment: Boolean = true) {
        tabHost?.currentTab = 0
        (childFragmentManager.findFragmentByTag(POST_MESSAGE_TAG) as? PostMessageFragment)
                ?.addLink(link)
    }

    override fun onResume() {
        super.onResume()
        if (!argFlagUsed && argFlag != 0) {
            argFlagUsed = true
            requireContext().post(Runnable {
                when (argFlag) {
                    R.id.photo -> onPhotoClick()
                    R.id.camera -> onCameraClick()
                    R.id.giphy -> onGiphyClick()
                }
            })
        }

        pendingGiphyLink?.let {
            (childFragmentManager.findFragmentByTag(POST_MESSAGE_TAG) as? PostMessageFragment)
                    ?.apply {
                        addGiphyLink(it)
                    }
            pendingGiphyLink = null
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
            requireContext().toast(R.string.err_fail)
        }
    }

    fun onCameraClick() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val cameraFileUri = requireContext().getRandomCameraFileUri()
            val photoURI = FileProvider.getUriForFile(requireContext(), BuildConfig.Authority, File(cameraFileUri))
            app().zumpaPrefs.lastCameraUri = photoURI.toString()
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            startActivityForResult(intent, REQ_CODE_CAMERA)
        } catch(e: Exception) {
            requireContext().toast(R.string.err_fail)
        }
    }

    fun onGiphyClick() {
        try {
            startActivityForResult(Intent(context, GiphyActivity::class.java), REQ_CODE_GIPHY)
        } catch(e: Exception) {
            requireContext().toast(R.string.err_fail)
        }
    }

    private fun createIndicator(@DrawableRes resId: Int, @ColorInt color: Int, parent: ViewGroup?): View {
        val context = requireContext()
        val btn = context.layoutInflater.inflate(R.layout.view_tab_button, parent, false) as ImageView
        val res = context.resources
        val icon = res.getDrawable(resId).wrapWithTint(color)
        btn.setImageDrawable(icon)
        return btn
    }

    override fun onDestroyView() {
        if (!isTablet && argThreadId == null) {
            (activity as? MainActivity)?.floatingButton?.showAnimated()
        }
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ARG_FLAG_USED, argFlagUsed)
    }
}