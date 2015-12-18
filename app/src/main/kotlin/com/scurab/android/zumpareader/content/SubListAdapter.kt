package com.scurab.android.zumpareader.content

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.annotation.ColorInt
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.drawable.SimpleProgressDrawable
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.find
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by JBruchanov on 27/11/2015.
 */
public class SubListAdapter : RecyclerView.Adapter<ZumpaSubItemViewHolder> {

    private val TYPE_ITEM = 1
    private val TYPE_IMAGE = 2
    private val TYPE_URL = 3

    private val dateFormat = SimpleDateFormat("HH:mm.ss")
    private val items: ArrayList<ZumpaThreadItem>
    private val dataItems: ArrayList<SubListItem>

    @ColorInt
    private var contextColor: Int = 0

    constructor(items: List<ZumpaThreadItem>) {
        this.items = ArrayList(items)
        this.dataItems = buildItems(items)
    }

    private fun buildItems(items: List<ZumpaThreadItem>): ArrayList<SubListItem> {
        val data = ArrayList<SubListItem>(items.size)
        val sub = ArrayList<SubListItem>()
        var position = 0
        for (item in items) {
            data.add(SubListItem(item, position, TYPE_ITEM, null))
            sub.clear()
            item.urls.exec {
                for (url in it) {
                    val element = SubListItem(item, position, if (url.isImage()) TYPE_IMAGE else TYPE_URL, url)
                    sub.add(element)
                }
                sub.sortBy { it.type }
                data.addAll(sub)
            }
            position++
        }
        return data
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        (recyclerView.context as Activity).exec {
            var outTypedValue = TypedValue()
            it.theme.resolveAttribute(R.attr.contextColor, outTypedValue, true);
            contextColor = outTypedValue.data
        }
    }

    override fun getItemCount(): Int {
        return dataItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return dataItems[position].type
    }

    internal fun updateItemForUrl(position: Int) {
        dataItems[position].type = TYPE_URL
        notifyItemChanged(position)
    }

    override fun onBindViewHolder(holder: ZumpaSubItemViewHolder, position: Int) {
        var dataItem = dataItems[position]
        val itemView = holder.itemView
        itemView.background.setLevel(dataItem.itemPosition % 2)
        when (getItemViewType(position)) {
            TYPE_ITEM -> {
                var item = dataItem.item
                holder.title.text = item.body
                holder.author.text = item.author
                holder.time.text = dateFormat.format(item.date)
            }
            TYPE_URL -> {
                var lastButton = (position + 1) > dataItems.size - 1 || dataItems[position + 1].type != TYPE_URL
                itemView.setPadding(itemView.paddingLeft, itemView.paddingTop, itemView.paddingRight, if (lastButton) itemView.paddingLeft else itemView.paddingLeft / 2)
                holder.button.text = dataItem.data
            }
            TYPE_IMAGE -> holder.loadImage(dataItem.data!!)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ZumpaSubItemViewHolder? {
        return parent.let {
            var li = LayoutInflater.from(it!!.context)
            when (viewType) {
                TYPE_ITEM -> {
                    ZumpaSubItemViewHolder(this, li.inflate(R.layout.item_sub_list, parent, false))
                }
                TYPE_URL -> ZumpaSubItemViewHolder(this, li.inflate(R.layout.item_sub_list_button, parent, false))
                TYPE_IMAGE -> {
                    val view = li.inflate(R.layout.item_sub_list_image, parent, false) as ImageView
                    view.adjustViewBounds = true
                    ZumpaSubItemViewHolder(this, view)
                }
                else -> throw IllegalStateException("Invalid view type:" + viewType)
            }
        }
    }

    fun updateItems(updated: List<ZumpaThreadItem>) {
        if (items.size != updated.size) {
            items.addAll(updated.subList(items.size, updated.size))
            notifyItemInserted(items.size)
        }
    }
}

private data class SubListItem(val item: ZumpaThreadItem, val itemPosition: Int, var type: Int, val data: String?)

public class ZumpaSubItemViewHolder(adapter: SubListAdapter, view: View) : ZumpaItemViewHolder(view) {
    internal val button by lazy { find<Button>(R.id.button) }
    internal val imageView by lazy { view as ImageView }
    internal val imageTarget by lazy { ItemTarget(adapter, this) }
    internal var url : String? = null
    internal var loadedUrl : String? = null

    public fun loadImage(url: String) {
        if (url.equals(loadedUrl)) {
            return
        }
        imageTarget.loading++
        this.url = url
        Picasso.with(imageView.context).load(url).into(imageTarget)
    }
}

internal class ItemTarget(val adapter: SubListAdapter, val holder: ZumpaSubItemViewHolder) : com.squareup.picasso.Target {
    var loading = 0

    var itemChangedNotifyAction = Runnable { adapter.notifyItemChanged(holder.adapterPosition) }
    val progressDrawable by lazy { SimpleProgressDrawable(holder.itemView.resources) }

    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
        holder.imageView.setImageDrawable(progressDrawable)
    }

    override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
        if (--loading == 0) {
            holder.loadedUrl = holder.url
            holder.imageView.setImageDrawable(BitmapDrawable(holder.itemView.context.resources, bitmap))
            holder.imageView.setOnClickListener { }
            holder.imageView.post(itemChangedNotifyAction)
        }
    }

    override fun onBitmapFailed(errorDrawable: Drawable?) {
        adapter.updateItemForUrl(holder.adapterPosition)
    }
}

private fun String.isImage(): Boolean {
    var uri = Uri.parse(this)
    val path = uri.path.toLowerCase()
    return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".bmp") || path.endsWith(".gif")
}