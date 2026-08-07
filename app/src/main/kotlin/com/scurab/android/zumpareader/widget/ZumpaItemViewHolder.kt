package com.scurab.android.zumpareader.widget

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.scurab.android.zumpareader.R

/**
 * Created by JBruchanov on 27/11/2015.
 */
abstract class ZumpaItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    internal val title by lazy { view.findViewById<TextView>(R.id.subject) }
    internal val author by lazy { view.findViewById<TextView>(R.id.author) }
    internal val time by lazy { view.findViewById<TextView>(R.id.time) }
    internal val threads by lazy { view.findViewById<TextView>(R.id.threads) }
}
