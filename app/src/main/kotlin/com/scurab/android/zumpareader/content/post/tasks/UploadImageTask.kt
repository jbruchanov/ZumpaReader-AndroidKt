package com.scurab.android.zumpareader.content.post.tasks

import android.os.AsyncTask
import com.scurab.android.zumpareader.utils.FotoDiskProvider

/**
 * Created by JBruchanov on 12/01/2016.
 */
abstract class UploadImageTask(val file: String) : AsyncTask<Void, Void, String>() {

    override fun doInBackground(vararg params: Void?): String? {
        try {
            return FotoDiskProvider.uploadPicture(file, null)
        } catch(t: Throwable) {
            return null
        }
    }

    override abstract fun onPostExecute(result: String?)
}