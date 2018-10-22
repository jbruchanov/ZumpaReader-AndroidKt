package com.scurab.android.zumpareader.content

import android.graphics.drawable.LevelListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.ui.DelayClickListener
import com.scurab.android.zumpareader.widget.ToggleAdapter
import com.scurab.android.zumpareader.widget.ToggleViewHolder
import org.jetbrains.anko.find
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by JBruchanov on 25/11/2015.
 */

class MainListAdapter : ToggleAdapter<MainListAdapter.ZumpaThreadViewHolder> {

    companion object {
        const val tThread = 0
        const val tThreadLongClick = 1
        const val tFavorite = 2
        const val tIgnore = 3
        const val tShare = 4
    }

    interface OnShowItemListener {
        fun onShowingItem(source: MainListAdapter, item: Int)
    }

    interface OnItemClickListener {
        fun onItemClick(item: ZumpaThread, position: Int, type: Int)
    }

    var onItemClickListener: OnItemClickListener? = null

    var items: ArrayList<ZumpaThread>

    private var selectedItem: ZumpaThread? = null
    private val dataMap: HashMap<String, ZumpaThread> = HashMap()
    private val dateFormat = SimpleDateFormat("dd.MM. HH:mm.ss", Locale.US)
    private val shoreDateFormat = SimpleDateFormat("HH:mm", Locale.US)
    private var onShowItemListener: OnShowItemListener? = null
    private var onShowItemListenerEndOffset: Int = 0

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
            dataMap[newItem.id] = newItem
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
        holder.apply {
            content.background.level = position % 2
            menu.background.level = position % 2
            title.text = item.styledSubject(holder.itemView.context)
            author.text = item.author
            threads.text = item.items.toString()
            time.text = if (item.lastAuthor == null) dateFormat.format(item.date) else shoreDateFormat.format(item.date)
            lastAuthor.text = item.lastAuthor
            (stateBar.background as? LevelListDrawable)?.level = item.state
            if (position == itemCount - onShowItemListenerEndOffset) {
                onShowItemListener?.onShowingItem(this@MainListAdapter, position)
            }
            itemView.isSelected = item == selectedItem
            isFavorite.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
            content.translationX = 0f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZumpaThreadViewHolder {
        return parent.let {
            val li = LayoutInflater.from(it.context)
            val zumpaThreadViewHolder = ZumpaThreadViewHolder(li.inflate(R.layout.item_main_list, parent, false))
            zumpaThreadViewHolder.apply {
                content.setOnClickListener(DelayClickListener {_ ->
                    if (isValidPosition()) {
                        dispatchItemClick(items[adapterPosition], adapterPosition, tThread)
                    }
                })
                favorite.setOnClickListener(DelayClickListener {_ ->
                    if (isValidPosition()) {
                        dispatchItemClick(items[adapterPosition], adapterPosition, tFavorite)
                    }
                })
                ignore.setOnClickListener(DelayClickListener {_ ->
                    if (isValidPosition()) {
                        dispatchItemClick(items[adapterPosition], adapterPosition, tIgnore)
                    }
                })
                share.setOnClickListener(DelayClickListener {_ ->
                    if (isValidPosition()) {
                        dispatchItemClick(items[adapterPosition], adapterPosition, tShare)
                    }
                })
                content.setOnLongClickListener {_ ->
                    if (isValidPosition()) {
                        dispatchItemLongClick(zumpaThreadViewHolder, items[adapterPosition], adapterPosition)
                    }
                    true
                }
            }
        }
    }

    private fun ZumpaThreadViewHolder.isValidPosition() =
            adapterPosition < items.size && adapterPosition >= 0

    private fun dispatchItemLongClick(vh: ZumpaThreadViewHolder, thread: ZumpaThread, position: Int) {
        onItemClickListener?.onItemClick(thread, position, tThreadLongClick)
    }

    private fun dispatchItemClick(zumpaThread: ZumpaThread, adapterPosition: Int, type: Int) {
        onItemClickListener?.onItemClick(zumpaThread, adapterPosition, type)
    }

    fun setOnShowItemListener(listener: OnShowItemListener, endOffset: Int) {
        onShowItemListener = listener
        onShowItemListenerEndOffset = endOffset
    }


    class ZumpaThreadViewHolder(view: View) : ZumpaItemViewHolder(view), ToggleViewHolder {
        val stateBar by lazy { itemView.find<View>(R.id.item_state) }
        val lastAuthor by lazy { itemView.find<TextView>(R.id.last_author) }
        val isFavorite by lazy { itemView.find<ImageView>(R.id.is_favorite) }
        val favorite by lazy { itemView.find<View>(R.id.favorite) }
        val ignore by lazy { itemView.find<View>(R.id.ignore) }
        val share by lazy { itemView.find<View>(R.id.share) }
        override val content by lazy { itemView.find<View>(R.id.item_thread_content) }
        override val menu by lazy { itemView.find<View>(R.id.item_thread_menu) }
    }

    fun removeItem(item: ZumpaThread) {
        dataMap.remove(item.id)
        val index = items.indexOf(item)
        if (index != -1) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }
}
