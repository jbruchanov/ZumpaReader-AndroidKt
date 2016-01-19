package com.scurab.android.zumpareader.event

import android.support.v4.app.DialogFragment

/**
 * Created by JBruchanov on 19/01/2016.
 */

public val DIALOG_EVENT_START = 1
public val DIALOG_EVENT_STOP = 2

public data class DialogEvent(public val type: Int, public val dialog: DialogFragment)