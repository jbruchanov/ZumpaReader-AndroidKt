package com.scurab.android.zumpareader.content

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.TextView
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.util.find

/**
 * Created by JBruchanov on 27/11/2015.
 */
public class ZumpaItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    internal val title by lazy { find<TextView>(R.id.subject) }
    internal val author by lazy { find<TextView>(R.id.author) }
    internal val time by lazy { find<TextView>(R.id.time) }
    internal val threads by lazy { find<TextView>(R.id.threads) }
}