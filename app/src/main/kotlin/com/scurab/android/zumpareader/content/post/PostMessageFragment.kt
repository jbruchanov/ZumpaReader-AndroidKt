package com.scurab.android.zumpareader.content.post

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.content.SendingFragment
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.execOn
import com.scurab.android.zumpareader.util.hideKeyboard
import com.scurab.android.zumpareader.util.toast
import com.scurab.android.zumpareader.widget.PostMessageView
import org.jetbrains.anko.find
import org.jetbrains.anko.support.v4.toast
import rx.Observer
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.*

/**
 * Created by JBruchanov on 31/12/2015.
 */
class PostMessageFragment : DialogFragment(), SendingFragment {

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

    private val postMessageView: PostMessageView? get() = view?.find<PostMessageView>(R.id.post_message_view)
    override var sendingDialog: ProgressDialog? = null

    val mainActivity: MainActivity? get() {
        return activity as MainActivity?
    }
    val zumpaApp: ZumpaReaderApp? get() {
        return mainActivity?.zumpaApp
    }

    private val parentPostFragment: PostFragment? get() = parentFragment as PostFragment?

    private val showKeyboard: Boolean by lazy { arguments?.getBoolean(SHOW_KEYBOARD) ?: false }
    private val argSubject: String? by lazy { arguments?.getString(Intent.EXTRA_SUBJECT) }
    private val argThreadId: String? by lazy { arguments?.getString(PostFragment.THREAD_ID) }
    private val argMessage: String? by lazy { arguments?.getString(Intent.EXTRA_TEXT) }

    private val links = ArrayList<String>()

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_post_message, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postMessageView.execOn {
            setUIForNewMessage()
            sendButton.setOnClickListener { dispatchSend() }
            subject.setText(argSubject)
            subject.isEnabled = argThreadId == null
            message.setText(ZumpaSimpleParser.replaceLinksByZumpaLinks(argMessage))

            camera.setOnClickListener { parentPostFragment?.onCameraClick() }
            photo.setOnClickListener { parentPostFragment?.onPhotoClick() }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (links.size > 0) {
            postMessageView?.message.exec {
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

        var postMessageView = this.postMessageView!!
        var subject = postMessageView.subject.text.toString().trim()
        var message = postMessageView.message.text.toString().trim()

        if (subject.isEmpty()) {
            context.toast(R.string.err_empty_subject)
            return
        }

        if (message.isEmpty()) {
            context.toast(R.string.err_empty_msg)
            return
        }

        zumpaApp?.zumpaAPI.exec {
            val app = zumpaApp!!
            val threadId = argThreadId
            isSending = true
            if (threadId == null) {
                val body = ZumpaThreadBody(app.zumpaPrefs.nickName, subject, message)
                context.hideKeyboard(view)
                it.sendThread(body)
                        .subscribeOn(Schedulers.io())
                        .subscribe(object : Observer<ZumpaThreadResult?> {
                            override fun onNext(t: ZumpaThreadResult?) {
                            }

                            override fun onError(e: Throwable?) {
                                isSending = false
                                e?.message.exec {
                                    toast(it)
                                }
                            }

                            override fun onCompleted() {
                                isSending = false
                                if (isResumed) {
                                    dismiss()
                                }
                            }
                        })
            } else {
                val body = ZumpaThreadBody(app.zumpaPrefs.nickName, app.zumpaData[threadId]?.subject ?: argSubject!!, message, threadId)
                val observable = it.sendResponse(threadId, threadId, body)
                context.hideKeyboard(view)
                observable
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(object : Observer<ZumpaThreadResult?> {
                            private var finish = false
                            override fun onNext(t: ZumpaThreadResult?) {
                                t.exec {
                                    if (isResumed) {
                                        isSending = false
                                        finish = true
                                    }
                                }
                            }

                            override fun onError(e: Throwable?) {
                                if (isResumed) {
                                    e?.message?.exec { toast(it) }
                                    isSending = false
                                }
                            }

                            override fun onCompleted() {
                                if (finish && isResumed) {
                                    dismiss()
                                }
                            }
                        })
            }
        }
    }

    override fun dismiss() {
        mainActivity.execOn {
            supportFragmentManager.popBackStack()
            reloadData()
        }
    }

    fun addLink(link: String) {
        links.add(link)
    }

    private fun String.asZumpaLinkWithNewLine(): String {
        return "<%s>\n".format(this)
    }

    private fun Editable.isLastCharNewLine(): Boolean {
        return this.length == 0 || this.last() == '\n'
    }
}