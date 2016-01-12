package com.scurab.android.zumpareader.content.post

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.DialogFragment
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pawegio.kandroid.find
import com.pawegio.kandroid.toast
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.content.SendingFragment
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.*
import com.scurab.android.zumpareader.widget.PostMessageView
import rx.Observer
import rx.schedulers.Schedulers
import java.util.*

/**
 * Created by JBruchanov on 31/12/2015.
 */
public class PostMessageFragment : DialogFragment(), SendingFragment {

    companion object {
        private val SHOW_KEYBOARD = "SHOW_KEYBOARD"
        private val LOCK_SUBJECT = "LOCK_SUBJECT"

        public fun newInstance(subject: String?, message: String?): PostMessageFragment {
            return PostMessageFragment().apply {
                arguments = PostFragment.arguments(subject, message)
            }
        }

        public fun arguments(subject: String?, message: String?, showKeyboard: Boolean = true, lockSubject: Boolean = false): Bundle {
            return Bundle().apply {
                putString(Intent.EXTRA_SUBJECT, subject)
                putString(Intent.EXTRA_TEXT, message)
                putBoolean(SHOW_KEYBOARD, showKeyboard)
                putBoolean(LOCK_SUBJECT, lockSubject)
            }
        }

        public val REQ_CODE_IMAGE = 123
        public val REQ_CODE_CAMERA = 124
    }

    private val postMessageView: PostMessageView? get() = view?.find<PostMessageView>(R.id.post_message_view)
    override var sendingDialog: ProgressDialog? = null

    public val mainActivity: MainActivity? get() {
        return activity as MainActivity?
    }
    public val zumpaApp: ZumpaReaderApp? get() {
        return mainActivity?.zumpaApp
    }

    private val argSubject: String? by lazy {
        arguments?.getString(Intent.EXTRA_SUBJECT)
    }

    private val showKeyboard: Boolean by lazy {
        arguments?.getBoolean(SHOW_KEYBOARD) ?: false
    }

    private val argMessage: String? by lazy { arguments?.getString(Intent.EXTRA_TEXT) }
    private val argLockSubject: Boolean by lazy { arguments?.getBoolean(LOCK_SUBJECT) ?: false }

    private val links = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(0, R.style.AppTheme_Dialog);
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_post_message, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postMessageView.execOn {
            setUIForNewMessage()
            sendButton.setOnClickListener { dispatchSend() }
            subject.setText(argSubject)
            subject.isEnabled = !argLockSubject
            message.setText(ZumpaSimpleParser.replaceLinksByZumpaLinks(argMessage))

            camera.setOnClickListener { onCameraClick() }
            photo.setOnClickListener { onPhotoClick() }
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

    private fun onPhotoClick() {
        try {
            val intent = Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            parentFragment.startActivityForResult(intent, REQ_CODE_IMAGE);
        } catch(e: Exception) {
            context.toast(R.string.err_fail)
        }
    }

    private fun onCameraClick() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val cameraFileUri = context.getRandomCameraFileUri()
            zumpaApp!!.zumpaPrefs.lastCameraUri = cameraFileUri
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.parse( cameraFileUri))
            parentFragment.startActivityForResult(intent, REQ_CODE_CAMERA);
        } catch(e: Exception) {
            context.toast(R.string.err_fail)
        }
    }

    protected fun dispatchSend() {
        if (postMessageView == null) {
            return
        }
        var postMessageView= this.postMessageView!!
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
            val body = ZumpaThreadBody(app.zumpaPrefs.nickName, subject, message)
            isSending = true
            context.hideKeyboard(view)
            it.sendThread(body)
                    .subscribeOn(Schedulers.io())
                    .subscribe(object : Observer<ZumpaThreadResult?> {
                        override fun onNext(t: ZumpaThreadResult?) {
                        }

                        override fun onError(e: Throwable?) {
                            isSending = false
                            e?.message.exec { if (view != null) view.post { toast(it) } } }

                        override fun onCompleted() {
                            isSending = false
                            if (isResumed) {
                                dismiss()
                            }
                        }
                    })
        }
    }

    override fun dismiss() {
        super.dismiss()
        mainActivity.execOn { reloadData() }
    }

    fun addLink(link: String) {
        links.add(link)
    }

    private fun String.asZumpaLinkWithNewLine() : String {
        return "<%s>\n".format(this)
    }

    private fun Editable.isLastCharNewLine(): Boolean {
        return this.length == 0 || this.last() == '\n'
    }
}