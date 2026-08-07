package com.scurab.android.zumpareader.ext

import android.content.Context
import com.scurab.android.zumpareader.ZumpaReaderApp

/**
 * Created by jbruchanov on 16/10/2017.
 */

/**
 * Get application as typed object
 */
fun Context.app(): ZumpaReaderApp = this.applicationContext as ZumpaReaderApp
