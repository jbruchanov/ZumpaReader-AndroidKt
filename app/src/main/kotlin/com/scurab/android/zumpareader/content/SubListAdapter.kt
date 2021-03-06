package com.scurab.android.zumpareader.content

import android.app.Activity
import android.graphics.drawable.Animatable
import androidx.annotation.ColorInt
import androidx.annotation.Nullable
import androidx.recyclerview.widget.RecyclerView
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.image.ImageInfo
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.model.SurveyItem
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.util.find
import com.scurab.android.zumpareader.util.isImageUri
import com.scurab.android.zumpareader.util.scaledImageRequest
import com.scurab.android.zumpareader.widget.SurveyView
import com.scurab.android.zumpareader.widget.ToggleAdapter
import com.scurab.android.zumpareader.widget.ToggleViewHolder
import org.jetbrains.anko.find
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by JBruchanov on 27/11/2015.
 */
class SubListAdapter : ToggleAdapter<ZumpaSubItemViewHolder> {

    interface ItemClickListener {
        fun onItemClick(position: Int, item: ZumpaThreadItem, longClick: Boolean, view: View)
        fun onItemClick(url: String, longClick: Boolean, view: View)
        fun onMenuItemClick(position: Int, item: ZumpaThreadItem, type: Int)
    }

    companion object {
        val tReply = 1
        val tCopy = 2
        val tSpeak = 3
    }

    private val TYPE_ITEM = 1
    private val TYPE_IMAGE = 2
    private val TYPE_URL = 3
    private val TYPE_SURVEY = 4

    private val dateFormat = SimpleDateFormat("HH:mm.ss", Locale.US)
    private val items: ArrayList<ZumpaThreadItem>
    private val dataItems: ArrayList<SubListItem>
    var itemClickListener: ItemClickListener? = null
    var loadImages: Boolean
    var surveyClickListner: SurveyView.ItemClickListener? = null

    @ColorInt
    private var contextColor: Int = 0

    constructor(data: List<ZumpaThreadItem>, loadImages: Boolean = true) {
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
            item.urls?.let {
                for (url in it) {
                    val element = SubListItem(item, i, if (url.isImageUri() && loadImages) TYPE_IMAGE else TYPE_URL, url)
                    sub.add(element)
                }
                sub.sortBy { it.type }
                outDataItems.addAll(sub)
            }
            if (i == 0 && item.survey != null) {
                outDataItems.add(SubListItem(item, i, TYPE_SURVEY, null))
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        (recyclerView.context as Activity).let {
            var outTypedValue = TypedValue()
            it.theme.resolveAttribute(R.attr.contextColor, outTypedValue, true)
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
        if (position >= 0 && position < dataItems.size) {
            dataItems[position].type = TYPE_URL
            notifyItemChanged(position)
        }
    }

    override fun onBindViewHolder(holder: ZumpaSubItemViewHolder, position: Int) {
        var dataItem = dataItems[position]
        val itemView = holder.itemView
        holder.content.background?.apply { level = dataItem.itemPosition % 2 }
        when (getItemViewType(position)) {
            TYPE_ITEM -> {
                val item = dataItem.item
                holder.title.text = item.styledBody(itemView.context)
                holder.author.text = item.styledAuthor(itemView.context)
                holder.time.text = dateFormat.format(item.date)
                holder.menu.background?.apply { level = dataItem.itemPosition % 2 }
                holder.content.translationX = 0f
            }
            TYPE_URL -> {
                val lastButton = (position + 1) > dataItems.size - 1 || dataItems[position + 1].type != TYPE_URL
                itemView.setPadding(itemView.paddingLeft, itemView.paddingTop, itemView.paddingRight, if (lastButton) itemView.paddingLeft else itemView.paddingLeft / 2)
                holder.button.text = dataItem.data
            }
            TYPE_IMAGE -> holder.loadImage(dataItem.data!!)
            TYPE_SURVEY -> holder.surveyView.survey = items[dataItem.itemPosition].survey
        }
        itemView.postInvalidate()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZumpaSubItemViewHolder {
        val li = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ITEM -> {
                val vh = ZumpaSubItemViewHolder(this, li.inflate(R.layout.item_sub_list, parent, false))
                vh.content.setOnClickListener { v -> dispatchClick(vh.adapterPosition, dataItems[vh.adapterPosition].item, v) }
                vh.content.setOnLongClickListener { v -> dispatchClick(vh.adapterPosition, dataItems[vh.adapterPosition].item, v, true); true }
                vh.menuReply.setOnClickListener { _ -> dispatchMenuItemClick(vh.adapterPosition, dataItems[vh.adapterPosition].item, tReply) }
                vh.menuCopy.setOnClickListener { _ -> dispatchMenuItemClick(vh.adapterPosition, dataItems[vh.adapterPosition].item, tCopy) }
                vh.menuSpeak.setOnClickListener { _ -> dispatchMenuItemClick(vh.adapterPosition, dataItems[vh.adapterPosition].item, tSpeak) }
                vh
            }
            TYPE_URL -> {
                val vh = ZumpaSubItemViewHolder(this, li.inflate(R.layout.item_sub_list_button, parent, false))
                vh.button.setOnClickListener { v -> dispatchClick(vh.button.text.toString(), v) }
                vh.button.setOnLongClickListener { v -> dispatchClick(vh.button.text.toString(), v, true); true }
                vh
            }
            TYPE_IMAGE -> {
                val view = li.inflate(R.layout.item_sub_list_image, parent, false)
                val vh = ZumpaSubItemViewHolder(this, view)
                view.setOnClickListener { v -> vh.loadedUrl?.let { dispatchClick(it, v) } }
                view.setOnLongClickListener { v -> vh.loadedUrl?.let { dispatchClick(it, v, true) }; true }
                vh
            }
            TYPE_SURVEY -> {
                val view = li.inflate(R.layout.item_sub_list_survey, parent, false) as SurveyView
                view.surveyItemClickListener = object : SurveyView.ItemClickListener {
                    override fun onItemClick(item: SurveyItem) {
                        surveyClickListner?.onItemClick(item)
                    }
                }
                ZumpaSubItemViewHolder(this, view)
            }
            else -> throw IllegalStateException("Invalid view type:$viewType")
        }
    }

    protected fun dispatchClick(position: Int, item: ZumpaThreadItem, view: View, longClick: Boolean = false) {
        itemClickListener?.onItemClick(position, item, longClick, view)
    }

    protected fun dispatchClick(url: String, view: View, longClick: Boolean = false) {
        itemClickListener?.onItemClick(url, longClick, view)
    }

    protected fun dispatchMenuItemClick(position: Int, item: ZumpaThreadItem, type: Int) {
        itemClickListener?.onMenuItemClick(position, item, type)
    }

    fun updateItems(updated: List<ZumpaThreadItem>, clearData: Boolean) {
        if (clearData) {
            items.clear()
            dataItems.clear()
        }
        if (items.size != updated.size) {
            val oldSize = dataItems.size
            items.addAll(updated.subList(items.size, updated.size))
            buildAdapterItems(items, dataItems)
            if (clearData) {
                notifyDataSetChanged()
            } else {
                notifyItemRangeInserted(oldSize, dataItems.size - oldSize)
            }
        } else if (items.size >= 0 && items[0].survey != null) {
            //update survey if necessary
            items[0].survey = updated[0].survey
            val index = dataItems.indexOfFirst { it.type == TYPE_SURVEY }
            notifyItemChanged(index)
        }
    }
}

private data class SubListItem(val item: ZumpaThreadItem, val itemPosition: Int, var type: Int, val data: String?)

class ZumpaSubItemViewHolder(val adapter: SubListAdapter, val view: View) : ZumpaItemViewHolder(view), ToggleViewHolder {
    override val content by lazy { itemView.find<View>(R.id.item_content) }
    override val menu by lazy { itemView.find<View>(R.id.item_menu) }
    internal val button by lazy { find<Button>(R.id.button) }
    internal val imageView by lazy { find<SimpleDraweeView>(R.id.image) }
    internal val imageViewOverlay by lazy { find<View>(R.id.overlay) }
    internal var url: String? = null
    internal var loadedUrl: String? = null
    internal var hasFailed: Boolean = false
    internal val surveyView by lazy { view as SurveyView }

    internal val menuReply by lazy {itemView.find<View>(R.id.reply)}
    internal val menuCopy by lazy {itemView.find<View>(R.id.copy)}
    internal val menuSpeak by lazy {itemView.find<View>(R.id.speak)}

    fun loadImage(url: String) {
        if (url == loadedUrl) {
            return
        }
        this.url = url
        hasFailed = false
        val controller = Fresco.newDraweeControllerBuilder()
                .setControllerListener(object : BaseControllerListener<ImageInfo>() {
                    override fun onFinalImageSet(id: String?, @Nullable imageInfo: ImageInfo?, @Nullable animatable: Animatable?) {
                        loadedUrl = url
                        imageInfo?.let {
                            val aspectRatio = it.width / it.height.toFloat()
                            imageView.aspectRatio = aspectRatio
                        }
                    }

                    override fun onFailure(id: String?, throwable: Throwable?) {
                        hasFailed = true
                        loadedUrl = url
                        imageView.aspectRatio = 5f
                    }
                })
                .setImageRequest(scaledImageRequest(url, imageView.context))
                .setAutoPlayAnimations(true)
                .build()
        imageView.aspectRatio = 16 / 9f
        imageView.controller = controller
    }
}