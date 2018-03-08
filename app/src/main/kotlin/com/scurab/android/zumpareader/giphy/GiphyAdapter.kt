package com.scurab.android.zumpareader.giphy

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.ViewGroup
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.view.SimpleDraweeView
import com.giphy.sdk.core.models.Media
import com.giphy.sdk.core.network.api.GPHApiClient
import com.scurab.android.zumpareader.R
import org.jetbrains.anko.padding
import kotlin.math.roundToInt
import kotlin.properties.Delegates

/**
 * Created by jbruchanov on 08/03/2018.
 */
class GiphyAdapter(private val giphyAPI: GPHApiClient) : RecyclerView.Adapter<GiphyViewHolder>() {

    private val loadingOffset = 5

    private var lastExpression: String? = null
    private val items = mutableListOf<Media>()
    var isLoading: Boolean = false
        private set(value) {
            if (value != field) {
                onLoadingListener?.invoke(value)
                field = value
            }
        }

    private var recyclerView: RecyclerView by Delegates.notNull()

    var onLoadingListener: ((loading: Boolean) -> Unit)? = null
    var onItemClickListener: ((item: Media) -> Unit)? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiphyViewHolder {
        val view = SimpleDraweeView(parent.context).apply {
            padding = resources.getDimensionPixelSize(R.dimen.gap_small)
            hierarchy.setPlaceholderImage(R.drawable.giphy_placeholder)
        }

        val giphyViewHolder = GiphyViewHolder(view)
        view.setOnClickListener {
            onItemClickListener?.invoke(items[giphyViewHolder.adapterPosition])
        }
        return giphyViewHolder
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: GiphyViewHolder, position: Int) {
        val item = items[position]
        holder.draweeView.apply {
            item.images.fixedWidth.let { image ->
                aspectRatio = image.width / image.height.toFloat()
                controller = Fresco.newDraweeControllerBuilder()
                        .setUri(image.gifUrl)
                        .setAutoPlayAnimations(true)
                        .build()
            }
        }
        if (position + loadingOffset > itemCount) {
            dispatchLoadNextPage()
        }
    }

    fun addItems(data: List<Media>) {
        val old = itemCount
        items.addAll(data)
        notifyItemRangeInserted(old, data.size)
    }

    fun dispatchLoadNextPage() {
        if (!isLoading) {
            lastExpression?.let {
                search(it, itemCount)
            }
        }
    }

    fun search(expr: String, offset: Int = 0) {
        val size = itemCount
        if (offset == 0) {
            items.clear()
            notifyItemRangeRemoved(0, size)
            recyclerView.scrollTo(0, 0)
        }
        if (expr.isNotEmpty()) {
            isLoading = true
            giphyAPI.search(expr, null, 50, offset, null, null, { result, err ->
                isLoading = false
                result?.let {
                    lastExpression = expr
                    addItems(it.data)
                }
            })
        }
    }

}

class GiphyViewHolder(val draweeView: SimpleDraweeView) : RecyclerView.ViewHolder(draweeView)