package com.scurab.android.zumpareader.content

import android.graphics.drawable.LevelListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ui.DelayClickListener
import com.scurab.android.zumpareader.widget.ToggleAdapter
import com.scurab.android.zumpareader.widget.ToggleViewHolder

/**
 * Created by JBruchanov on 25/11/2015.
 *
 * Takes a whole rendered list per emission - it owns no data of its own any more, the ViewModel
 * merges, sorts and decorates.
 */
class MainListAdapter : ToggleAdapter<MainListAdapter.ZumpaThreadViewHolder>() {

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
        fun onItemClick(item: RenderedThreadRow, position: Int, type: Int)
    }

    var onItemClickListener: OnItemClickListener? = null

    var items: List<RenderedThreadRow> = emptyList()
        private set

    private var onShowItemListener: OnShowItemListener? = null
    private var onShowItemListenerEndOffset: Int = 0

    /**
     * The only incremental notify the old adapter had was `removeItem`, for the slide-out when a
     * thread is ignored. Everything else already went through notifyDataSetChanged, so recognising
     * a single removal is enough to keep the behaviour identical.
     */
    fun setItems(newItems: List<RenderedThreadRow>) {
        val old = items
        items = newItems
        val removedAt = old.singleRemovalIndex(newItems)
        if (removedAt >= 0) {
            notifyItemRemoved(removedAt)
        } else {
            notifyDataSetChanged()
        }
    }

    private fun List<RenderedThreadRow>.singleRemovalIndex(new: List<RenderedThreadRow>): Int {
        if (size - new.size != 1) {
            return -1
        }
        val index = indices.firstOrNull { it >= new.size || this[it].id != new[it].id } ?: return -1
        val tailMatches = (index until new.size).all { this[it + 1].id == new[it].id }
        return if (tailMatches) index else -1
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ZumpaThreadViewHolder, position: Int) {
        val item = items[position]
        holder.apply {
            content.background.level = position % 2
            menu.background.level = position % 2
            title.text = item.subject
            author.text = item.author
            threads.text = item.answerCount
            time.text = item.time
            lastAuthor.text = item.lastAuthor
            (stateBar.background as? LevelListDrawable)?.level = item.state.ordinal
            if (position == itemCount - onShowItemListenerEndOffset) {
                onShowItemListener?.onShowingItem(this@MainListAdapter, position)
            }
            itemView.isSelected = item.isSelected
            isFavorite.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
            content.translationX = 0f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZumpaThreadViewHolder {
        val li = LayoutInflater.from(parent.context)
        return ZumpaThreadViewHolder(li.inflate(R.layout.item_main_list, parent, false)).apply {
            content.setOnClickListener(DelayClickListener { dispatch(tThread) })
            favorite.setOnClickListener(DelayClickListener { dispatch(tFavorite) })
            ignore.setOnClickListener(DelayClickListener { dispatch(tIgnore) })
            share.setOnClickListener(DelayClickListener { dispatch(tShare) })
            content.setOnLongClickListener {
                dispatch(tThreadLongClick)
                true
            }
        }
    }

    private fun ZumpaThreadViewHolder.dispatch(type: Int) {
        val position = adapterPosition
        if (position in items.indices) {
            onItemClickListener?.onItemClick(items[position], position, type)
        }
    }

    fun setOnShowItemListener(listener: OnShowItemListener, endOffset: Int) {
        onShowItemListener = listener
        onShowItemListenerEndOffset = endOffset
    }

    class ZumpaThreadViewHolder(view: View) : ZumpaItemViewHolder(view), ToggleViewHolder {
        val stateBar by lazy { itemView.findViewById<View>(R.id.item_state) }
        val lastAuthor by lazy { itemView.findViewById<TextView>(R.id.last_author) }
        val isFavorite by lazy { itemView.findViewById<ImageView>(R.id.is_favorite) }
        val favorite by lazy { itemView.findViewById<View>(R.id.favorite) }
        val ignore by lazy { itemView.findViewById<View>(R.id.ignore) }
        val share by lazy { itemView.findViewById<View>(R.id.share) }
        override val content by lazy { itemView.findViewById<View>(R.id.item_thread_content) }
        override val menu by lazy { itemView.findViewById<View>(R.id.item_thread_menu) }
    }
}
