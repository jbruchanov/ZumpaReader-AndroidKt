package com.scurab.android.zumpareader.content.post

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.content.SendingFragment
import com.scurab.android.zumpareader.extension.app
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.RxTransformers
import com.scurab.android.zumpareader.util.hideKeyboard
import com.scurab.android.zumpareader.util.toast
import com.scurab.android.zumpareader.widget.PostMessageView
import com.trello.rxlifecycle2.components.support.RxDialogFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.find
import org.jetbrains.anko.support.v4.toast
import java.util.*

/**
 * Created by JBruchanov on 31/12/2015.
 */
class PostMessageFragment : RxDialogFragment(), SendingFragment {

    companion object {
        private val SHOW_KEYBOARD = "SHOW_KEYBOARD"

        fun newInstance(subject: String?, message: String?): PostMessageFragment {
            return PostMessageFragment().apply {
                arguments = PostFragment.arguments(subject, message)
            }
        }

        fun arguments(subject: String?, message: String?, showKeyboard: Boolean = true, threadId: String? = null): Bundle {
            return Bundle().apply {
                putString(Intent.EXTRA_SUBJECT, subject)
                putString(Intent.EXTRA_TEXT, message)
                putBoolean(SHOW_KEYBOARD, showKeyboard)
                putString(PostFragment.THREAD_ID, threadId)
            }
        }
    }

    private val postMessageView: PostMessageView? get() = view?.find(R.id.post_message_view)
    override var sendingDialog: ProgressDialog? = null

    val mainActivity: MainActivity? get() {
        return activity as MainActivity?
    }
    val zumpaApp: ZumpaReaderApp get() = app()

    private val parentPostFragment: PostFragment? get() = parentFragment as PostFragment?

    private val showKeyboard: Boolean by lazy { arguments?.getBoolean(SHOW_KEYBOARD) ?: false }
    private val argSubject: String? by lazy { arguments?.getString(Intent.EXTRA_SUBJECT) }
    private val argThreadId: String? by lazy { arguments?.getString(PostFragment.THREAD_ID) }
    private val argMessage: String? by lazy { arguments?.getString(Intent.EXTRA_TEXT) }

    private val links = ArrayList<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_post_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postMessageView?.apply {
            setUIForNewMessage()
            sendButton.setOnClickListener { dispatchSend() }
            subject.setText(argSubject)
            subject.isEnabled = argThreadId == null
            message.setText(ZumpaSimpleParser.replaceLinksByZumpaLinks(argMessage))

            camera.setOnClickListener { parentPostFragment?.onCameraClick() }
            photo.setOnClickListener { parentPostFragment?.onPhotoClick() }
            giphy.setOnClickListener { parentPostFragment?.onGiphyClick() }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (links.size > 0) {
            postMessageView?.message?.let {
                if (!it.text.isLastCharNewLine()) {
                    it.append("\n")
                }
                for (link in links) {
                    it.text.append(link.asZumpaLinkWithNewLine())
                }
            }
            links.clear()
        }
    }

    protected fun dispatchSend() {
        if (postMessageView == null) {
            return
        }

        val postMessageView = this.postMessageView!!
        val subject = postMessageView.subject.text.toString().trim()
        val message = postMessageView.message.text.toString().trim()
        val context = requireContext()

        if (subject.isEmpty()) {
            context.toast(R.string.err_empty_subject)
            return
        }

        if (message.isEmpty()) {
            context.toast(R.string.err_empty_msg)
            return
        }

        zumpaApp.zumpaAPI.let { api ->
            val threadId = argThreadId
            isSending = true
            if (threadId == null) {
                val body = ZumpaThreadBody(zumpaApp.zumpaPrefs.nickName, subject, message)
                context.hideKeyboard(view)
                api.sendThread(body)
                        .subscribeOn(Schedulers.io())
                        .compose(bindToLifecycle<ZumpaThreadResult>())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(RxTransformers.zumpaRedirectHandler())
                        .subscribe(
                                { result ->
                                    dismiss()
                                },
                                { err ->
                                    err.message?.let { toast(it) }
                                    isSending = false
                                }
                        )
            } else {
                val body = ZumpaThreadBody(zumpaApp.zumpaPrefs.nickName, zumpaApp.zumpaData[threadId]?.subject ?: argSubject!!, message, threadId)
                api.sendResponse(threadId, threadId, body)
                        .subscribeOn(Schedulers.io())
                        .compose(bindToLifecycle<ZumpaThreadResult>())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(RxTransformers.zumpaRedirectHandler())
                        .subscribe(
                                { result ->
                                    dismiss()
                                },
                                { err ->
                                    err.message?.let { toast(it) }
                                    isSending = false
                                }
                        )
            }
        }
    }

    override fun dismiss() {
        isSending = false
        (parentFragment as PostFragment).dismissAllowingStateLoss()
        mainActivity?.apply {
            reloadData()
        }
    }

    fun addLink(link: String) {
        links.add(link)
    }

    fun addGiphyLink(link: String) {
        postMessageView?.message?.append(link.asZumpaLinkWithNewLine())
    }

    private fun String.asZumpaLinkWithNewLine(): String {
        return "<%s>\n".format(this)
    }

    private fun Editable.isLastCharNewLine(): Boolean {
        return this.length == 0 || this.last() == '\n'
    }
}