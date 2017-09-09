package com.scurab.android.zumpareader.content

import android.graphics.drawable.LevelListDrawable
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.ui.DelayClickListener
import com.scurab.android.zumpareader.util.exec
import org.jetbrains.anko.find
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by JBruchanov on 25/11/2015.
 */
class MainListAdapter : RecyclerView.Adapter<MainListAdapter.ZumpaThreadViewHolder> {

    interface OnShowItemListener {
        fun onShowingItem(source: MainListAdapter, item: Int)
    }

    interface OnItemClickListener {
        fun onItemClick(item: ZumpaThread, position: Int)
    }

    var onItemClickListener: OnItemClickListener? = null

    var items: ArrayList<ZumpaThread>

    private var selectedItem: ZumpaThread? = null
    private val dataMap: HashMap<String, ZumpaThread> = HashMap()
    private var ownerRecyclerView: RecyclerView? = null
    private val dateFormat = SimpleDateFormat("dd.MM. HH:mm.ss")
    private val shoreDateFormat = SimpleDateFormat("HH:mm")
    private var onShowItemListener: OnShowItemListener? = null
    private var onShoItemListenerEndOffset: Int = 0

    constructor(data: ArrayList<ZumpaThread>) : super() {
        items = ArrayList(data)
        dataMap.putAll(items.associateBy { it.id })
    }

    fun setSelectedItem(thread: ZumpaThread?, position: Int) {
        if (selectedItem != null && ownerRecyclerView != null) {
            val rv = ownerRecyclerView!!
            for (i in 0..rv.childCount) {
                val childAt = rv.getChildAt(i)
                if (childAt != null) {
                    val vh = rv.getChildViewHolder(childAt)
                    if (selectedItem == items[vh.adapterPosition]) {
                        notifyItemChanged(vh.adapterPosition)
                        break
                    }
                }
            }
        }
        selectedItem = thread
        notifyItemChanged(position)
    }

    fun addItems(newItems: ArrayList<ZumpaThread>) {
        for (newItem in newItems) {
            //need to rewrite old stuff
            dataMap.put(newItem.id, newItem)
        }
        items.clear()
        items.addAll(dataMap.values)
        items.sortByDescending { it.idLong }
        notifyDataSetChanged()
    }

    fun removeAll() {
        items.clear()
        dataMap.clear()
        try {
            notifyDataSetChanged()
        } catch(e: Exception) {
            ownerRecyclerView?.post { notifyDataSetChanged() }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: ZumpaThreadViewHolder, position: Int) {
        var item = items[position]
        holder.itemView.background.level = position % 2
        holder.title.text = item.styledSubject(holder.itemView.context)
        holder.author.text = item.author
        holder.threads.text = item.items.toString()
        holder.time.text = if (item.lastAuthor == null) dateFormat.format(item.date) else shoreDateFormat.format(item.date)
        holder.lastAuthor.text = item.lastAuthor
        (holder.stateBar.background as? LevelListDrawable).exec {
            it.level = item.state
        }
        if (position == itemCount - onShoItemListenerEndOffset) {
            onShowItemListener?.onShowingItem(this, position)
        }
        holder.itemView.isSelected = item == selectedItem
        holder.isFavorite.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
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

    fun setOnShowItemListener(listener: OnShowItemListener, endOffset: Int) {
        onShowItemListener = listener
        onShoItemListenerEndOffset = endOffset
    }


    class ZumpaThreadViewHolder(view: View) : ZumpaItemViewHolder(view) {
        val stateBar by lazy { itemView.find<View>(R.id.item_state) }
        val lastAuthor by lazy { itemView.find<TextView>(R.id.last_author) }
        val isFavorite by lazy { itemView.find<ImageView>(R.id.is_favorite) }
    }
}
