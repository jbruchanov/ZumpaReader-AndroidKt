package com.scurab.android.zumpareader.ui

import android.animation.Animator
import android.os.Build
import android.support.annotation.IdRes
import android.support.v4.view.ViewCompat
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewAnimationUtils
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.execIfNull

/**
 * Created by jiribruchanov on 12/8/15.
 */

public fun <T : View> T.hideAnimated() {
    changeVisibilityAnimated(false)
}

public fun <T : View> T.showAnimated() {
    changeVisibilityAnimated(true)
}

public fun <T : View> T.isVisible(): Boolean {
    return visibility == View.VISIBLE;
}

public fun <T : View> T.isHidden(): Boolean {
    return visibility == View.INVISIBLE;
}

public fun <T : View> T.isGone(): Boolean {
    return visibility == View.GONE;
}

public fun <T : View> T.changeVisibilityAnimated(show: Boolean) {
    val visible = visibility == View.VISIBLE && translationY == 0f && translationX == 0f && alpha == 1f;
    if (visible != show) {
        val animListener: AnimatorListener = object : AnimatorListener() {
            override fun onAnimationStart(view: View?, animation: Animator?) {
                if (show) {
                    visibility = View.VISIBLE
                }
            }

            override fun onAnimationEnd(view: View?, animation: Animator?) {
                if (!show) {
                    visibility = View.INVISIBLE
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isAttachedToWindow) {
            var init = 0f
            var max = Math.max(width, height).toFloat()
            var animator = ViewAnimationUtils.createCircularReveal(this, width / 2, height / 2, if (show) init else max, if (show) max else init)
            animator.exec {
                it.addListener(animListener);
                it.start()
            }
        } else {
            translationY = 0f
            translationX = 0f
            alpha = if(show) 0f else 1f
            visibility = View.VISIBLE
            ViewCompat.animate(this).alpha(if (show) 1f else 0f).setDuration(250).setListener(animListener).start()
        }
    }
}