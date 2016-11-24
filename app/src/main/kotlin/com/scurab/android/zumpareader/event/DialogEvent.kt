package com.scurab.android.zumpareader.event

import android.support.v4.app.DialogFragment

/**
 * Created by JBruchanov on 19/01/2016.
 */

val DIALOG_EVENT_START = 1
val DIALOG_EVENT_STOP = 2

data class DialogEvent(val type: Int, val dialog: DialogFragment)