package com.scurab.android.zumpareader.content

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.model.ZumpaThread
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by JBruchanov on 25/11/2015.
 */
public class MainListAdapter : RecyclerView.Adapter<ZumpaItemViewHolder> {

    public interface OnShowItemListener {
        public fun onShowingItem(source: MainListAdapter, item: Int);
    }

    public interface OnItemClickListener {
        public fun onItemClick(item: ZumpaThread);
    }

    public var onItemClickListener: OnItemClickListener? = null

    public val items: ArrayList<ZumpaThread> = ArrayList()
    private val dataMap: HashSet<ZumpaThread> = HashSet()
    private var ownerRecyclerView: RecyclerView? = null
    private val dateFormat = SimpleDateFormat("HH:mm.ss dd.MM.")
    private var onShowItemListener: OnShowItemListener? = null
    private var onShoItemListenerEndOffset: Int = 0

    constructor(items: ArrayList<ZumpaThread>) : super() {
        this.items.addAll(items)
        dataMap.addAll(items)
    }

    public fun addItems(newItems: ArrayList<ZumpaThread>) {
        var oldSize = dataMap.size
        dataMap.addAll(newItems)
        items.clear()
        items.addAll(dataMap)
        items.sortByDescending { it.idLong }
        if (dataMap.size != oldSize) {
            notifyItemRangeInserted(oldSize, dataMap.size - oldSize)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: ZumpaItemViewHolder, position: Int) {
        var item = items[position]
        holder.itemView.background.setLevel(position % 2)
        holder.title.text = item.subject
        holder.author.text = item.author
        holder.threads.text = item.threads.toString()
//        holder.threads.text = position.toString()
        holder.time.text = dateFormat.format(item.date)

        if (position == itemCount - onShoItemListenerEndOffset) {
            onShowItemListener?.onShowingItem(this, position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ZumpaItemViewHolder? {
        return parent.let {
            var li = LayoutInflater.from(it!!.context)
            ZumpaItemViewHolder(li.inflate(R.layout.item_main_list, parent, false)).apply {
                itemView.setOnClickListener { dispatchItemClick(items[adapterPosition]) }
            }
        }
    }

    private fun dispatchItemClick(zumpaThread: ZumpaThread) {
        onItemClickListener?.onItemClick(zumpaThread)
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
}
