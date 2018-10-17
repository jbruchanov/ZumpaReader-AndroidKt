package com.scurab.android.zumpareader.content

import android.app.ProgressDialog
import android.content.Context
import com.scurab.android.zumpareader.R

/**
 * Created by JBruchanov on 31/12/2015.
 */
interface SendingFragment {

    var sendingDialog: ProgressDialog?

    var isSending: Boolean
        get() {
            return sendingDialog != null
        }
        set(value) {
            if (value != isSending) {
                if (value) {
                    requireContext().let {
                        sendingDialog = createDialog(it).apply { show() }
                    }
                } else {
                    sendingDialog?.dismiss()
                    sendingDialog = null
                }
            }
        }

    private fun createDialog(context: Context): ProgressDialog {
        val dialog = ProgressDialog(context, R.style.AppTheme_Dialog)
        dialog.setTitle(null)
        dialog.setMessage(context.resources.getString(R.string.wheeeee))
        dialog.isIndeterminate = true
        dialog.setCancelable(false)
        dialog.setOnCancelListener(null)
        return dialog
    }

    fun requireContext() : Context
}