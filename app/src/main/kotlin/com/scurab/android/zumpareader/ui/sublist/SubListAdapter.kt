package com.scurab.android.zumpareader.ui.sublist

import android.app.Activity
import android.graphics.drawable.Animatable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.ColorInt
import androidx.annotation.Nullable
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.image.ImageInfo
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.util.findViewById
import com.scurab.android.zumpareader.util.scaledImageRequest
import com.scurab.android.zumpareader.widget.SurveyView
import com.scurab.android.zumpareader.widget.ToggleAdapter
import com.scurab.android.zumpareader.widget.ToggleViewHolder
import com.scurab.android.zumpareader.widget.ZumpaItemViewHolder

/**
 * Created by JBruchanov on 27/11/2015.
 *
 * Takes a whole rendered list per emission. The flattening of a message into its message/link/
 * image/survey rows moved to [SubListViewModel] - the adapter only picks a view type and binds.
 */
class SubListAdapter : ToggleAdapter<ZumpaSubItemViewHolder>() {

    interface ItemClickListener {
        fun onItemClick(position: Int, item: RenderedSubListRow.Message, longClick: Boolean, view: View)
        fun onItemClick(url: String, longClick: Boolean, view: View)
        fun onMenuItemClick(position: Int, item: RenderedSubListRow.Message, type: Int)
    }

    companion object {
        const val tReply = 1
        const val tCopy = 2
        const val tSpeak = 3

        private const val TYPE_ITEM = 1
        private const val TYPE_IMAGE = 2
        private const val TYPE_URL = 3
        private const val TYPE_SURVEY = 4
    }

    var items: List<RenderedSubListRow> = emptyList()
        private set

    var itemClickListener: ItemClickListener? = null
    var surveyClickListner: SurveyView.ItemClickListener? = null

    @ColorInt
    private var contextColor: Int = 0

    /**
     * New answers arrive at the end, which is the case worth keeping cheap: a plain
     * notifyDataSetChanged there would drop the insert animation and disturb the scroll position
     * of someone reading the thread. A survey vote changes exactly one row. Anything else - a
     * thread switch, a reload - is a full rebind, as it always was.
     */
    fun setItems(newItems: List<RenderedSubListRow>) {
        val old = items
        items = newItems
        when {
            old.isEmpty() || newItems.isEmpty() -> notifyDataSetChanged()

            newItems.size > old.size && newItems.subList(0, old.size) == old ->
                notifyItemRangeInserted(old.size, newItems.size - old.size)

            newItems.size == old.size -> {
                val changed = old.indices.filter { old[it] != newItems[it] }
                when (changed.size) {
                    0 -> Unit
                    1 -> notifyItemChanged(changed.single())
                    else -> notifyDataSetChanged()
                }
            }

            else -> notifyDataSetChanged()
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        (recyclerView.context as Activity).let {
            val outTypedValue = TypedValue()
            it.theme.resolveAttribute(R.attr.contextColor, outTypedValue, true)
            contextColor = outTypedValue.data
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is RenderedSubListRow.Message -> TYPE_ITEM
        is RenderedSubListRow.Image -> TYPE_IMAGE
        is RenderedSubListRow.Link -> TYPE_URL
        is RenderedSubListRow.Survey -> TYPE_SURVEY
    }

    override fun onBindViewHolder(holder: ZumpaSubItemViewHolder, position: Int) {
        val row = items[position]
        val itemView = holder.itemView
        holder.content.background?.apply { level = row.itemIndex % 2 }
        when (row) {
            is RenderedSubListRow.Message -> {
                holder.title.text = row.body
                holder.author.text = row.author
                holder.time.text = row.time
                holder.menu.background?.apply { level = row.itemIndex % 2 }
                holder.content.translationX = 0f
            }

            is RenderedSubListRow.Link -> {
                val lastButton = (position + 1) > items.size - 1 ||
                        items[position + 1] !is RenderedSubListRow.Link
                itemView.setPadding(
                    itemView.paddingLeft,
                    itemView.paddingTop,
                    itemView.paddingRight,
                    if (lastButton) itemView.paddingLeft else itemView.paddingLeft / 2
                )
                holder.button.text = row.url
            }

            is RenderedSubListRow.Image -> holder.loadImage(row.url)
            is RenderedSubListRow.Survey -> holder.surveyView.survey = row.survey
        }
        itemView.postInvalidate()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZumpaSubItemViewHolder {
        val li = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ITEM -> {
                val vh = ZumpaSubItemViewHolder(this, li.inflate(R.layout.item_sub_list, parent, false))
                vh.content.setOnClickListener { v -> vh.dispatchClick(v, longClick = false) }
                vh.content.setOnLongClickListener { v -> vh.dispatchClick(v, longClick = true); true }
                vh.menuReply.setOnClickListener { vh.dispatchMenuClick(tReply) }
                vh.menuCopy.setOnClickListener { vh.dispatchMenuClick(tCopy) }
                vh.menuSpeak.setOnClickListener { vh.dispatchMenuClick(tSpeak) }
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
                    override fun onItemClick(item: SurveyItemUiState) {
                        surveyClickListner?.onItemClick(item)
                    }
                }
                ZumpaSubItemViewHolder(this, view)
            }

            else -> throw IllegalStateException("Invalid view type:$viewType")
        }
    }

    private fun ZumpaSubItemViewHolder.message(): RenderedSubListRow.Message? =
        items.getOrNull(adapterPosition) as? RenderedSubListRow.Message

    private fun ZumpaSubItemViewHolder.dispatchClick(view: View, longClick: Boolean) {
        message()?.let { itemClickListener?.onItemClick(adapterPosition, it, longClick, view) }
    }

    private fun ZumpaSubItemViewHolder.dispatchMenuClick(type: Int) {
        message()?.let { itemClickListener?.onMenuItemClick(adapterPosition, it, type) }
    }

    private fun dispatchClick(url: String, view: View, longClick: Boolean = false) {
        itemClickListener?.onItemClick(url, longClick, view)
    }
}

class ZumpaSubItemViewHolder(val adapter: SubListAdapter, val view: View) : ZumpaItemViewHolder(view), ToggleViewHolder {
    override val content by lazy { itemView.findViewById<View>(R.id.item_content) }
    override val menu by lazy { itemView.findViewById<View>(R.id.item_menu) }
    internal val button by lazy { findViewById<Button>(R.id.button) }
    internal val imageView by lazy { findViewById<SimpleDraweeView>(R.id.image) }
    internal val imageViewOverlay by lazy { findViewById<View>(R.id.overlay) }
    internal var url: String? = null
    internal var loadedUrl: String? = null
    internal var hasFailed: Boolean = false
    internal val surveyView by lazy { view as SurveyView }

    internal val menuReply by lazy { itemView.findViewById<View>(R.id.reply) }
    internal val menuCopy by lazy { itemView.findViewById<View>(R.id.copy) }
    internal val menuSpeak by lazy { itemView.findViewById<View>(R.id.speak) }

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
