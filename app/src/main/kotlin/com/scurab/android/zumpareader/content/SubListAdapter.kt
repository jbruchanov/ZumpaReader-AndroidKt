package com.scurab.android.zumpareader.content

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import java.text.SimpleDateFormat

/**
 * Created by JBruchanov on 27/11/2015.
 */
public class SubListAdapter(private var items: List<ZumpaThreadItem>) : RecyclerView.Adapter<ZumpaItemViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm.ss")

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: ZumpaItemViewHolder, position: Int) {
        var item = items[position]
        holder.itemView.background.setLevel(position % 2)
        holder.title.text = item.body
        holder.author.text = item.author
        holder.time.text = dateFormat.format(item.date)
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ZumpaItemViewHolder? {
        return parent.let {
            var li = LayoutInflater.from(it!!.context)
            ZumpaItemViewHolder(li.inflate(R.layout.item_sub_list, parent, false))
        }
    }
}