package com.scurab.android.zumpareader.ui.offline

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.arch.collectWhileStarted
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.util.asVisibility
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 15/01/2016.
 */
class OfflineDownloadFragment : DialogFragment() {

    private val viewModel: OfflineDownloadViewModel by viewModel()

    private val start: Button get() = requireView().findViewById(R.id.start)
    private val stop: Button get() = requireView().findViewById(R.id.stop)
    private val threads: TextView get() = requireView().findViewById(R.id.threads)
    private val images: TextView get() = requireView().findViewById(R.id.images)
    private val pages: EditText get() = requireView().findViewById(R.id.pages)
    private val imagesDownload: CheckBox get() = requireView().findViewById(R.id.images_download)
    private val progressBar: ProgressBar get() = requireView().findViewById(R.id.progress_bar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppTheme_Dialog_Offline)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnKeyListener { _, keyCode, _ ->
                KeyEvent.KEYCODE_BACK == keyCode && !viewModel.uiState.value.isDismissable
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_offline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        start.setOnClickListener { viewModel.onStartClick() }
        stop.setOnClickListener { viewModel.onStopClick() }
        imagesDownload.setOnCheckedChangeListener { _, checked ->
            viewModel.onDownloadImagesChanged(checked)
        }
        pages.addTextChangedListener(pagesWatcher)
        //seed the ViewModel with whatever the layout starts with
        viewModel.onPagesChanged(pages.text.toString())
        viewModel.onDownloadImagesChanged(imagesDownload.isChecked)

        viewModel.uiState
            .map { it.isRunning }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { isRunning ->
                progressBar.visibility = isRunning.asVisibility(View.INVISIBLE)
                start.isEnabled = !isRunning
                pages.isEnabled = !isRunning
            }

        viewModel.uiState
            .map { it.threadsDownloaded }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { threads.text = it.toString() }

        viewModel.uiState
            .map { it.imagesDownloaded to it.imagesTotal }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { (done, total) ->
                images.text = "%s/%s".format(done, total)
            }

        viewModel.effects.collectWhileStarted(viewLifecycleOwner) { onEffect(it) }
    }

    private fun onEffect(effect: UiEffect) {
        when (effect) {
            is OfflineDownloadEffect.Dismiss -> dismissAllowingStateLoss()
            is ShowToast -> effect.text?.let { toast(it) } ?: toast(effect.resId)
            else -> Unit
        }
    }

    private val pagesWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            viewModel.onPagesChanged(s?.toString() ?: "")
        }
    }

    override fun onDestroyView() {
        pages.removeTextChangedListener(pagesWatcher)
        super.onDestroyView()
    }
}
