package com.scurab.android.zumpareader.content

import android.graphics.drawable.LevelListDrawable
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.pawegio.kandroid.find
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.ui.DelayClickListener
import com.scurab.android.zumpareader.util.exec
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by JBruchanov on 25/11/2015.
 */
public class MainListAdapter : RecyclerView.Adapter<MainListAdapter.ZumpaThreadViewHolder> {

    public interface OnShowItemListener {
        public fun onShowingItem(source: MainListAdapter, item: Int);
    }

    public interface OnItemClickListener {
        public fun onItemClick(item: ZumpaThread, position: Int);
    }

    public var onItemClickListener: OnItemClickListener? = null

    public var items: ArrayList<ZumpaThread>
    private val dataMap: HashMap<String, ZumpaThread> = HashMap()
    private var ownerRecyclerView: RecyclerView? = null
    private val dateFormat = SimpleDateFormat("dd.MM. HH:mm.ss")
    private val shoreDateFormat = SimpleDateFormat("HH:mm")
    private var onShowItemListener: OnShowItemListener? = null
    private var onShoItemListenerEndOffset: Int = 0

    constructor(data: ArrayList<ZumpaThread>) : super() {
        items = ArrayList(data)
        dataMap.putAll(items.toMapBy { it.id })
    }

    public fun addItems(newItems: ArrayList<ZumpaThread>) {
        for (newItem in newItems) {
            dataMap.put(newItem.id, newItem);
        }
        items.clear()
        items.addAll(dataMap.values)
        items.sortByDescending { it.idLong }
        notifyDataSetChanged()
    }

    public fun removeAll() {
        items.clear()
        dataMap.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: ZumpaThreadViewHolder, position: Int) {
        var item = items[position]
        holder.itemView.background.setLevel(position % 2)
        holder.title.text = item.styledSubject(holder.itemView.context)
        holder.author.text = item.author
        holder.threads.text = item.items.toString()
        holder.time.text = if (item.lastAuthor == null) dateFormat.format(item.date) else shoreDateFormat.format(item.date)
        holder.lastAuthor.text = item.lastAuthor
        (holder.stateBar.background as? LevelListDrawable).exec {
            it.setLevel(item.state)
        }
        if (position == itemCount - onShoItemListenerEndOffset) {
            onShowItemListener?.onShowingItem(this, position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ZumpaThreadViewHolder? {
        return parent.let {
            var li = LayoutInflater.from(it!!.context)
            ZumpaThreadViewHolder(li.inflate(R.layout.item_main_list, parent, false)).apply {
                itemView.setOnClickListener(DelayClickListener {
                    if (adapterPosition < items.size && adapterPosition >= 0) {
                        dispatchItemClick(items[adapterPosition], adapterPosition)
                    }
                })
            }
        }
    }

    private fun dispatchItemClick(zumpaThread: ZumpaThread, adapterPosition: Int) {
        onItemClickListener?.onItemClick(zumpaThread, adapterPosition)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView?) {
        super.onAttachedToRecyclerView(recyclerView)
        ownerRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView?) {
        super.onDetachedFromRecyclerView(recyclerView)
        ownerRecyclerView = null
    }

    public fun setOnShowItemListener(listener: OnShowItemListener, endOffset: Int) {
        onShowItemListener = listener
        onShoItemListenerEndOffset = endOffset
    }


    public class ZumpaThreadViewHolder(view: View) : ZumpaItemViewHolder(view) {
        val stateBar by lazy { itemView.find<View>(R.id.item_state) }
        val lastAuthor by lazy { itemView.find<TextView>(R.id.last_author) }
    }
}
