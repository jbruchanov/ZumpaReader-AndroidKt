package com.scurab.android.zumpareader.content

import android.app.Dialog
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.DialogFragment
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.pawegio.kandroid.find
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.data.LoaderTask
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.ui.isVisible
import com.scurab.android.zumpareader.util.asVisibility
import java.io.File
import java.util.*

/**
 * Created by JBruchanov on 15/01/2016.
 */

public class OfflineDownloadFragment : DialogFragment() {

    public val zumpaApp: ZumpaReaderApp?
        get() {
            return context.applicationContext as? ZumpaReaderApp
        }

    private val start: Button  get() = view!!.find<Button>(R.id.start)
    private val stop: Button get() = view!!.find<Button>(R.id.stop)
    private val threads: TextView get() = view!!.find<TextView>(R.id.threads)
    private val images: TextView get() = view!!.find<TextView>(R.id.images)
    private val pages: EditText get() = view!!.find<EditText>(R.id.pages)
    private val imagesDownload: CheckBox get() = view!!.find<CheckBox>(R.id.images_download)
    private val progressBar: ProgressBar get() = view!!.find<ProgressBar>(R.id.progress_bar)

    private var isLoading: Boolean
        get() {
            return progressBar.isVisible()
        }
        set(value) {
            progressBar.visibility = value.asVisibility(View.INVISIBLE)
            start.isEnabled = !value
            pages.isEnabled = !value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(0, R.style.AppTheme_Dialog_Offline)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog? {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnKeyListener { dialogInterface, i, keyEvent ->
            (KeyEvent.KEYCODE_BACK == i && isLoading)
        }
        return dialog;
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_offline, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        start.setOnClickListener { onStartLoading() }
        stop.setOnClickListener { onStopLoading() }
    }

    private fun onStopLoading() {
        if (isLoading) {
            isLoading = false
        } else {
            dismissAllowingStateLoss()
        }
    }

    override fun onStop() {
        super.onStop()
        loaderTask?.cancel(true)
    }

    private var loaderTask: LoaderTask? = null

    private fun onStartLoading() {
        isLoading = true
        val offline = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline.json").absolutePath
        loaderTask = object : LoaderTask(context.applicationContext as ZumpaReaderApp, pages.text.toString().toInt(), imagesDownload.isChecked, offline) {

            override fun onPostExecute(result: LinkedHashMap<String, ZumpaThread>?) {
                if (isResumed) {
                    isLoading = false
                }
            }

            override fun notifyProgressChanged() {
                this@OfflineDownloadFragment.images.post(progressChangedAction)
            }

            private val progressChangedAction = Runnable {
                this@OfflineDownloadFragment.images.text = "%s/%s".format(imagesDownloaded, imagesDownloading)
                this@OfflineDownloadFragment.threads.text = threadsDownloaded.toString()
            }
        }.apply { execute() }
    }


}