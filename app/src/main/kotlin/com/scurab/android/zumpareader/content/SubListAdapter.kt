package com.scurab.android.zumpareader.content

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.*
import android.net.Uri
import android.os.Build
import android.support.annotation.ColorInt
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.drawable.SimpleProgressDrawable
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.util.*
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by JBruchanov on 27/11/2015.
 */
public class SubListAdapter : RecyclerView.Adapter<ZumpaSubItemViewHolder> {

    public interface ItemClickListener {
        fun onItemClick(item: ZumpaThreadItem, longClick: Boolean)
        fun onItemClick(url: String, longClick: Boolean)
    }

    private val TYPE_ITEM = 1
    private val TYPE_IMAGE = 2
    private val TYPE_URL = 3

    private val dateFormat = SimpleDateFormat("HH:mm.ss")
    private val items: ArrayList<ZumpaThreadItem>
    private val dataItems: ArrayList<SubListItem>
    public var itemClickListener: ItemClickListener? = null
    public var loadImages : Boolean

    @ColorInt
    private var contextColor: Int = 0

    constructor(data: List<ZumpaThreadItem>, loadImages : Boolean = true) {
        items = ArrayList(data)
        this.loadImages = loadImages
        dataItems = ArrayList((items.size * 1.3/*some bigger values for links etc*/).toInt())
        buildAdapterItems(items, dataItems)
    }

    private fun buildAdapterItems(items: List<ZumpaThreadItem>, outDataItems: ArrayList<SubListItem>) {
        val pos = outDataItems.lastOrNull()?.itemPosition
        var lastIndex = if (pos != null) pos + 1 else 0//empty start with 0, otherwise with first newOne

        val sub = ArrayList<SubListItem>()
        for (i in lastIndex..items.size - 1) {
            val item = items[i]
            outDataItems.add(SubListItem(item, i, TYPE_ITEM, null))
            sub.clear()
            item.urls.exec {
                for (url in it) {
                    val element = SubListItem(item, i, if (url.isImage() && loadImages) TYPE_IMAGE else TYPE_URL, url)
                    sub.add(element)
                }
                sub.sortBy { it.type }
                outDataItems.addAll(sub)
            }
        }
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
        itemView.background.execOn {
            setLevel(dataItem.itemPosition % 2)
        }
        when (getItemViewType(position)) {
            TYPE_ITEM -> {
                var item = dataItem.item
                holder.title.text = item.styledBody(itemView.context)
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
        itemView.postInvalidate()
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ZumpaSubItemViewHolder? {
        return parent.let {
            var li = LayoutInflater.from(it!!.context)
            when (viewType) {
                TYPE_ITEM -> {
                    var vh = ZumpaSubItemViewHolder(this, li.inflate(R.layout.item_sub_list, parent, false))
                    vh.itemView.setOnClickListener { dispatchClick(dataItems[vh.adapterPosition].item) }
                    vh.itemView.setOnLongClickListener { dispatchClick(dataItems[vh.adapterPosition].item, true); true }
                    vh
                }
                TYPE_URL -> {
                    var vh = ZumpaSubItemViewHolder(this, li.inflate(R.layout.item_sub_list_button, parent, false))
                    vh.button.setOnClickListener { dispatchClick(vh.button.text.toString()) }
                    vh.button.setOnLongClickListener { dispatchClick(vh.button.text.toString(), true); true }
                    vh
                }
                TYPE_IMAGE -> {
                    val view = li.inflate(R.layout.item_sub_list_image, parent, false) as ImageView
                    view.adjustViewBounds = true
                    val vh = ZumpaSubItemViewHolder(this, view)
                    view.setOnClickListener { vh.loadedUrl.exec { dispatchClick(it) } }
                    view.setOnLongClickListener { vh.loadedUrl.exec { dispatchClick(it, true) }; true }
                    vh
                }
                else -> throw IllegalStateException("Invalid view type:" + viewType)
            }
        }
    }

    protected fun dispatchClick(item: ZumpaThreadItem, longClick : Boolean = false) {
        itemClickListener.exec { it.onItemClick(item, longClick) }
    }

    protected fun dispatchClick(url: String, longClick : Boolean = false) {
        itemClickListener.exec { it.onItemClick(url, longClick) }
    }

    fun updateItems(updated: List<ZumpaThreadItem>) {
        if (items.size != updated.size) {
            val oldSize = dataItems.size
            items.addAll(updated.subList(items.size, updated.size))
            buildAdapterItems(items, dataItems)
            notifyItemRangeInserted(oldSize, dataItems.size - oldSize - 1)
        }
    }
}

private data class SubListItem(val item: ZumpaThreadItem, val itemPosition: Int, var type: Int, val data: String?)

public class ZumpaSubItemViewHolder(val adapter: SubListAdapter, val view: View) : ZumpaItemViewHolder(view) {
    internal val button by lazy { find<Button>(R.id.button) }
    internal val imageView by lazy { view as ImageView }
    internal var url : String? = null
    internal var loadedUrl : String? = null
    internal var imageTarget : ItemTarget? = null

    public fun loadImage(url: String) {
        if (url.equals(loadedUrl)) {
            return
        }
        this.url = url
        imageTarget = ItemTarget(adapter, this, view.context.obtainStyledColor(R.attr.contextColor50p));
        Picasso.with(imageView.context).load(url).into(imageTarget)
    }
}

internal class ItemTarget(val adapter: SubListAdapter, val holder: ZumpaSubItemViewHolder, @ColorInt val contextColor:Int) : com.squareup.picasso.Target {
    val progressDrawable by lazy { SimpleProgressDrawable(holder.itemView.context) }

    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
        holder.imageView.setImageDrawable(progressDrawable)
    }

    override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
        holder.loadedUrl = holder.url
        holder.imageView.setImageDrawable(createDrawable(holder.itemView.context.resources, bitmap))
        holder.imageView.invalidate()
    }

    override fun onBitmapFailed(errorDrawable: Drawable?) {
        adapter.updateItemForUrl(holder.adapterPosition)
    }

    fun createDrawable(res: Resources, bitmap: Bitmap): Drawable {
        val img = BitmapDrawable(res, bitmap)
        var result: Drawable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            result = RippleDrawable(ColorStateList.valueOf(contextColor), img, null)
        } else {
            var pressed = LayerDrawable(arrayOf(img, ColorDrawable(contextColor)))
            result = StateListDrawable()
            result.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            result.addState(intArrayOf(), img)
        }
        return result
    }
}

private fun String.isImage(): Boolean {
    var uri = Uri.parse(this)
    val path = uri.path.toLowerCase()
    return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".bmp") || path.endsWith(".gif")
}