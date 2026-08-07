package com.scurab.android.zumpareader.ui

import android.app.ProgressDialog
import android.content.Context
import com.scurab.android.zumpareader.R

/**
 * Replaces the `SendingFragment` interface, whose `var sendingDialog: ProgressDialog?` setter *was*
 * the state. `isSending` is a boolean in the screen's ui state now and this only reacts to it.
 *
 * ProgressDialog is deprecated; swapping it for an inline overlay is a compose-phase job, not a
 * reason to grow this migration.
 */
class SendingDialogController(private val context: Context) {

    private var dialog: ProgressDialog? = null

    fun update(isSending: Boolean) {
        if (isSending) {
            if (dialog == null) {
                dialog = createDialog().apply { show() }
            }
        } else {
            dialog?.dismiss()
            dialog = null
        }
    }

    private fun createDialog(): ProgressDialog {
        return ProgressDialog(context, R.style.AppTheme_Dialog).apply {
            setTitle(null)
            setMessage(context.resources.getString(R.string.wheeeee))
            isIndeterminate = true
            setCancelable(false)
            setOnCancelListener(null)
        }
    }
}
