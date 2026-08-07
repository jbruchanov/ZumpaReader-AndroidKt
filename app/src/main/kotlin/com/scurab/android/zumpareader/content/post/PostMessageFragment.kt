package com.scurab.android.zumpareader.content.post

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.collectWhileStarted
import com.scurab.android.zumpareader.ui.SendingDialogController
import com.scurab.android.zumpareader.widget.PostMessageView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 31/12/2015.
 *
 * The message tab of [PostFragment]. It binds to the parent's [PostViewModel] rather than owning
 * one - the subject and the message are the dialog's state, and the image tabs need to append
 * their uploaded links to the same draft.
 */
class PostMessageFragment : Fragment() {

    private val viewModel: PostViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private val postMessageView: PostMessageView? get() = view?.findViewById(R.id.post_message_view)
    private val sendingDialog by lazy { SendingDialogController(requireContext()) }

    private var isSettingText = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_post_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postMessageView?.apply {
            setUIForNewMessage()
            sendButton.setOnClickListener { viewModel.onSend() }
            camera.setOnClickListener { viewModel.onCameraClick() }
            photo.setOnClickListener { viewModel.onPhotoClick() }
            subject.addTextChangedListener(subjectWatcher)
            message.addTextChangedListener(messageWatcher)
        }

        viewModel.uiState
            .map { it.subject to it.isSubjectEditable }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { (text, editable) ->
                postMessageView?.subject?.let {
                    it.isEnabled = editable
                    if (it.text.toString() != text) {
                        setTextKeepingCursor { it.setText(text) }
                    }
                }
            }

        viewModel.uiState
            .map { it.message }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { text ->
                postMessageView?.message?.let {
                    if (it.text.toString() != text) {
                        setTextKeepingCursor {
                            it.setText(text)
                            it.setSelection(it.length())
                        }
                    }
                }
            }

        viewModel.uiState
            .map { it.canSend }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { postMessageView?.sendButton?.isEnabled = it }

        viewModel.uiState
            .map { it.isSending }
            .distinctUntilChanged()
            .collectWhileStarted(viewLifecycleOwner) { sendingDialog.update(it) }
    }

    private inline fun setTextKeepingCursor(block: () -> Unit) {
        isSettingText = true
        block()
        isSettingText = false
    }

    private val subjectWatcher = watcher { viewModel.onSubjectChanged(it) }

    private val messageWatcher = watcher { viewModel.onMessageChanged(it) }

    private inline fun watcher(crossinline onChanged: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (!isSettingText) {
                onChanged(s?.toString() ?: "")
            }
        }
    }

    override fun onDestroyView() {
        sendingDialog.update(false)
        postMessageView?.subject?.removeTextChangedListener(subjectWatcher)
        postMessageView?.message?.removeTextChangedListener(messageWatcher)
        super.onDestroyView()
    }
}
