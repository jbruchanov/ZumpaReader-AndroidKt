package com.scurab.android.zumpareader.ui.post

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TabWidget
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentTabHost
import com.scurab.android.zumpareader.BuildConfig
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.HideKeyboard
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.arch.collectWhileStarted
import com.scurab.android.zumpareader.ext.layoutInflater
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.ui.BaseDialogFragment
import com.scurab.android.zumpareader.ui.main.MainActivity
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.getRandomCameraFileUri
import com.scurab.android.zumpareader.util.hideKeyboard
import com.scurab.android.zumpareader.util.obtainStyledColor
import com.scurab.android.zumpareader.util.post
import com.scurab.android.zumpareader.util.wrapWithTint
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 08/01/2016.
 */
class PostFragment : BaseDialogFragment() {

    companion object {
        const val THREAD_ID = "THREAD_UD"
        const val FLAG = "FLAG"

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
    }

    private val viewModel: PostViewModel by viewModel()

    private val tabHost: FragmentTabHost? get() = view?.findViewById(android.R.id.tabhost)
    private val tabWidget: TabWidget? get() = view?.findViewById(android.R.id.tabs)
    private val contextColor by lazy { requireContext().obtainStyledColor(R.attr.contextColor) }

    /** Tabs already handed to the FragmentTabHost - it has no way to remove one. */
    private val addedTabTags = HashSet<String>()

    private var cameraTargetUri: Uri? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraTargetUri
        cameraTargetUri = null
        if (saved && uri != null) {
            viewModel.onImagePicked(uri, fromCamera = true)
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onImagePicked(it, fromCamera = false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity?.let { it.post { it.hideFloatingButton() } }
        viewModel.start(
            PostArgs(
                subject = arguments?.getString(Intent.EXTRA_SUBJECT),
                message = arguments?.getString(Intent.EXTRA_TEXT),
                uris = arguments?.getParcelableArray(Intent.EXTRA_STREAM)
                    ?.filterIsInstance<Uri>()
                    .orEmpty(),
                threadId = arguments?.getString(THREAD_ID),
            )
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_post, container, false)
        view.findViewById<FragmentTabHost>(android.R.id.tabhost).apply {
            setup(context, childFragmentManager, android.R.id.tabcontent)
            setOnTabChangedListener { tag ->
                context.hideKeyboard(view)
                viewModel.onTabSelected(tag)
            }
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.uiState
            .map { it.tabs }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { syncTabs(it) }

        viewModel.uiState
            .map { it.selectedTabTag }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { tag ->
                tag?.takeIf { addedTabTags.contains(it) }?.let { wanted ->
                    tabHost?.let { host -> host.post { host.setCurrentTabByTag(wanted) } }
                }
            }

        viewModel.effects.collectWhileStarted(viewLifecycleOwner) { onEffect(it) }

        viewModel.onFlag(arguments?.getInt(FLAG) ?: 0)
    }

    /** FragmentTabHost only ever grows, so this adds what is new and leaves the rest alone. */
    private fun syncTabs(tabs: List<PostTabUiState>) {
        val host = tabHost ?: return
        tabs.filterNot { addedTabTags.contains(it.tag) }.forEach { tab ->
            when (tab) {
                is PostTabUiState.Message -> host.addTab(
                    host.newTabSpec(tab.tag)
                        .setIndicator(createIndicator(R.drawable.ic_pen, contextColor, tabWidget)),
                    PostMessageFragment::class.java,
                    null,
                )

                is PostTabUiState.Image -> host.addTab(
                    host.newTabSpec(tab.tag)
                        .setIndicator(createIndicator(tab.iconRes, contextColor, tabWidget)),
                    PostImageFragment::class.java,
                    PostImageFragment.arguments(tab.uri),
                )
            }
            addedTabTags += tab.tag
        }
    }

    private fun onEffect(effect: UiEffect) {
        when (effect) {
            is PostEffect.RequestCameraImage -> onCameraClick()
            is PostEffect.RequestGalleryImage -> onPhotoClick()
            is PostEffect.Dismiss -> dismissAllowingStateLoss()

            is HideKeyboard -> requireContext().hideKeyboard(view)
            is ShowToast -> effect.text?.let { toast(it) } ?: toast(effect.resId)
            else -> Unit
        }
    }

    fun onPhotoClick() {
        try {
            pickImage.launch("image/*")
        } catch (e: Exception) {
            toast(R.string.err_fail)
        }
    }

    /**
     * TakePicture takes the destination uri as its input, so the old round trip through
     * `ZumpaPrefs.lastCameraUri` is gone.
     */
    fun onCameraClick() {
        try {
            val file = File(requireContext().getRandomCameraFileUri())
            val uri = FileProvider.getUriForFile(requireContext(), BuildConfig.Authority, file)
            cameraTargetUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            toast(R.string.err_fail)
        }
    }

    private fun createIndicator(@DrawableRes resId: Int, @ColorInt color: Int, parent: ViewGroup?): View {
        val context = requireContext()
        val btn = context.layoutInflater.inflate(R.layout.view_tab_button, parent, false) as ImageView
        btn.setImageDrawable(context.resources.getDrawable(resId).wrapWithTint(color))
        return btn
    }

    override fun onDestroyView() {
        if (!isTablet && arguments?.getString(THREAD_ID) == null) {
            (activity as? MainActivity)?.floatingButton?.showAnimated()
        }
        addedTabTags.clear()
        super.onDestroyView()
    }
}
