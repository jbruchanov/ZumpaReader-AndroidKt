package com.scurab.android.zumpareader.content

import android.app.ProgressDialog
import android.content.Context
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.util.exec

/**
 * Created by JBruchanov on 31/12/2015.
 */
public interface SendingFragment {

    var sendingDialog: ProgressDialog?

    public var isSending: Boolean
        get() {
            return sendingDialog != null
        }
        set(value) {
            if (value != isSending) {
                if (value) {
                    getContext().exec {
                        sendingDialog = createDialog(it).apply { show() }
                    }
                } else {
                    sendingDialog.exec {
                        it.dismiss()
                    }
                    sendingDialog = null
                }
            }
        }

    private fun createDialog(context : Context): ProgressDialog {
        val dialog = ProgressDialog(context, R.style.AppTheme_Dialog)
        dialog.setTitle(null)
        dialog.setMessage(context.resources.getString(R.string.wheeeee))
        dialog.isIndeterminate = true
        dialog.setCancelable(false)
        dialog.setOnCancelListener(null)
        return dialog
    }
    public fun getContext(): Context
}