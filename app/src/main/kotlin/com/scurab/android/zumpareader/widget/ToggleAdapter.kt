package com.scurab.android.zumpareader.widget

import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.animation.DecelerateInterpolator

abstract class ToggleAdapter<VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    protected var ownerRecyclerView: RecyclerView? = null

    private val decelerateInterpolator = DecelerateInterpolator()

    fun toggleOpenState(position: Int) {
        ownerRecyclerView?.findViewHolderForAdapterPosition(position)?.let {
            toggleOpenState(it as ToggleViewHolder)
        }
    }

    fun closeMenu(position: Int) {
        ownerRecyclerView
                ?.findViewHolderForAdapterPosition(position)
                ?.let { it as? ToggleViewHolder }
                ?.takeIf { it.content.translationX > 0f }
                ?.let {
                    toggleOpenState(it)
                }
    }

    private fun toggleOpenState(vh: ToggleViewHolder) {
        val offset = if (vh.content.translationX == 0f) vh.menu.width.toFloat() else -vh.content.translationX
        vh.content.animate().translationXBy(offset).setInterpolator(decelerateInterpolator).start()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        ownerRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        ownerRecyclerView = null
    }
}

interface ToggleViewHolder {
    val menu : View
    val content: View
}