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
                        sendingDialog = ProgressDialog.show(it, null, it.resources.getString(R.string.wheeeee), true, false)
                    }
                } else {
                    sendingDialog.exec {
                        it.dismiss()
                    }
                    sendingDialog = null
                }
            }
        }

    public fun getContext(): Context
}