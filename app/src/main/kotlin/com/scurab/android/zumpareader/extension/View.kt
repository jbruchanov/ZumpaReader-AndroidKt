package com.scurab.android.zumpareader.extension

import android.os.Looper
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup

/**
 * Created by jbruchanov on 07/11/2017.
 */

/**
 * Post a runnable if running in non-UI thread, otherwise execute it immediately
 */
inline fun View.postIfNecessary(crossinline func: () -> Unit) {
    if (Looper.getMainLooper() == Looper.myLooper()) {
        func()
    } else {
        post { func() }
    }
}

/**
 * Find position of the view
 */
fun View.findRecyclerViewPosition(): Int {
    var view: View? = this
    while (view != null) {
        (view.layoutParams as? RecyclerView.LayoutParams)?.let {
            return it.viewAdapterPosition
        }
        view = view.parent as? View
    }
    throw IllegalStateException("Not found")
}

fun View.setVisibilitySafe(visible: Boolean) {
    postIfNecessary { visibility = if (visible) View.VISIBLE else View.GONE }
}

fun View.marginHorizontal(): Int {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams
    return (lp?.leftMargin ?: 0) + (lp?.rightMargin ?: 0)
}

fun View.paddingHorizontal(): Int = paddingLeft + paddingRight

fun View.hideKeyboard() = context.hideKeyboard(this)
fun View.showKeyboard() = context.showKeyboard(this)
fun View.postDelayed(delay: Long, runnable: () -> Unit) = postDelayed(runnable, delay)
fun View.isLandscape() = context.isLandscape()
fun View.isVisible() = this.visibility == View.VISIBLE

fun Boolean.toViewVisibility(invisibility: Int = View.GONE) = if (this == true) View.VISIBLE else invisibility

fun ViewGroup.firstChild(): View = getChildAt(0)
fun ViewGroup.lastChild(): View = getChildAt(childCount - 1)