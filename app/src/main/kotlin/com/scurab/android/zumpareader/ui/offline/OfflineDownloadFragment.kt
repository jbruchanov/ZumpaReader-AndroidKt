package com.scurab.android.zumpareader.ui.offline

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ui.compose.zumpaContent

/**
 * Created by JBruchanov on 15/01/2016.
 */
class OfflineDownloadFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppTheme_Dialog_Offline)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            //the screen's BackHandler decides, this only stops the dialog swallowing it first
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = zumpaContent { OfflineDownloadScreen() }
}
