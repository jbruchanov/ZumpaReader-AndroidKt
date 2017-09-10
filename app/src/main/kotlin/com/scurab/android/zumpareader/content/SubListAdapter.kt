package com.scurab.android.zumpareader.content

import android.app.Activity
import android.graphics.drawable.Animatable
import android.net.Uri
import android.support.annotation.ColorInt
import android.support.annotation.Nullable
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.image.ImageInfo
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.model.SurveyItem
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.util.*
import com.scurab.android.zumpareader.widget.SurveyView
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by JBruchanov on 27/11/2015.
 */
class SubListAdapter : RecyclerView.Adapter<ZumpaSubItemViewHolder> {

    interface ItemClickListener {
        fun onItemClick(item: ZumpaThreadItem, longClick: Boolean, view: View)
        fun onItemClick(url: String, longClick: Boolean, view: View)
    }

    private val TYPE_ITEM = 1
    private val TYPE_IMAGE = 2
    private val TYPE_URL = 3
    private val TYPE_SURVEY = 4

    private val dateFormat = SimpleDateFormat("HH:mm.ss")
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
            item.urls.exec {
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
        (recyclerView.context as Activity).exec {
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
        itemView.background.execOn {
            level = dataItem.itemPosition % 2
        }
        when (getItemViewType(position)) {
            TYPE_ITEM -> {
                var item = dataItem.item
                holder.title.text = item.styledBody(itemView.context)
                holder.author.text = item.styledAuthor(itemView.context)
                holder.time.text = dateFormat.format(item.date)
            }
            TYPE_URL -> {
                var lastButton = (position + 1) > dataItems.size - 1 || dataItems[position + 1].type != TYPE_URL
                itemView.setPadding(itemView.paddingLeft, itemView.paddingTop, itemView.paddingRight, if (lastButton) itemView.paddingLeft else itemView.paddingLeft / 2)
                holder.button.text = dataItem.data
            }
            TYPE_IMAGE -> holder.loadImage(dataItem.data!!)
            TYPE_SURVEY -> holder.surveyView.survey = items[dataItem.itemPosition].survey
        }
        itemView.postInvalidate()
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ZumpaSubItemViewHolder? {
        return parent.let {
            var li = LayoutInflater.from(it!!.context)
            when (viewType) {
                TYPE_ITEM -> {
                    var vh = ZumpaSubItemViewHolder(this, li.inflate(R.layout.item_sub_list, parent, false))
                    vh.itemView.setOnClickListener { v -> dispatchClick(dataItems[vh.adapterPosition].item, v) }
                    vh.itemView.setOnLongClickListener { v -> dispatchClick(dataItems[vh.adapterPosition].item, v, true); true }
                    vh
                }
                TYPE_URL -> {
                    var vh = ZumpaSubItemViewHolder(this, li.inflate(R.layout.item_sub_list_button, parent, false))
                    vh.button.setOnClickListener { v -> dispatchClick(vh.button.text.toString(), v) }
                    vh.button.setOnLongClickListener { v -> dispatchClick(vh.button.text.toString(), v, true); true }
                    vh
                }
                TYPE_IMAGE -> {
                    val view = li.inflate(R.layout.item_sub_list_image, parent, false) as SimpleDraweeView
//                    view.adjustViewBounds = true
                    val vh = ZumpaSubItemViewHolder(this, view)
                    view.setOnClickListener { v -> vh.loadedUrl.exec { dispatchClick(it, v) } }
                    view.setOnLongClickListener { v -> vh.loadedUrl.exec { dispatchClick(it, v, true) }; true }
                    vh
                }
                TYPE_SURVEY -> {
                    val view = li.inflate(R.layout.item_sub_list_survey, parent, false) as SurveyView
                    view.surveyItemClickListener = object : SurveyView.ItemClickListener {
                        override fun onItemClick(item: SurveyItem) {
                            surveyClickListner.execOn { onItemClick(item) }
                        }
                    }
                    ZumpaSubItemViewHolder(this, view)
                }
                else -> throw IllegalStateException("Invalid view type:" + viewType)
            }
        }
    }

    protected fun dispatchClick(item: ZumpaThreadItem, view: View, longClick: Boolean = false) {
        itemClickListener.exec { it.onItemClick(item, longClick, view) }
    }

    protected fun dispatchClick(url: String, view: View, longClick: Boolean = false) {
        itemClickListener.exec { it.onItemClick(url, longClick, view) }
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

class ZumpaSubItemViewHolder(val adapter: SubListAdapter, val view: View) : ZumpaItemViewHolder(view) {
    internal val button by lazy { find<Button>(R.id.button) }
    internal val imageView by lazy { view as SimpleDraweeView }
    internal var url: String? = null
    internal var loadedUrl: String? = null
    internal var hasFailed: Boolean = false
    internal val surveyView by lazy { view as SurveyView }

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
                        imageInfo.exec {
                            val aspectRatio = it.width / it.height.toFloat()
                            imageView.aspectRatio = aspectRatio
                        }
                    }

                    override fun onFailure(id: String?, throwable: Throwable?) {
                        hasFailed = true
                        loadedUrl = url
                    }
                })
                .setImageRequest(scaledImageRequest(url, imageView.context))
                .setAutoPlayAnimations(true)
                .build()
        imageView.aspectRatio = 1.778f
        imageView.controller = controller
    }
}