package com.scurab.android.zumpareader.content

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pawegio.kandroid.find
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.showKeyboard
import com.scurab.android.zumpareader.widget.PostMessageView

/**
 * Created by JBruchanov on 31/12/2015.
 */
public class PostMessageDialog : DialogFragment() {

    private val postMessageView: PostMessageView get() = view!!.find<PostMessageView>(R.id.post_message_view)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog? {
        var dialog = super.onCreateDialog(savedInstanceState)
        dialog.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_post, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postMessageView.setUIForNewMessage()
    }

    override fun onResume() {
        super.onResume()
        postMessageView.post {
            context.showKeyboard(postMessageView.subject)
        }
    }

    override fun onDestroyView() {
        (activity as? MainActivity).exec {
            it.floatingButton.showAnimated()
        }
        super.onDestroyView()
    }
}