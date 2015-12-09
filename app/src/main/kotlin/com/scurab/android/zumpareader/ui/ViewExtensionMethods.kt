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
    val visible = visibility == View.VISIBLE;
    if (visible != show) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isAttachedToWindow) {
            var init = 0f
            var max = Math.max(width, height).toFloat()
            var animator = ViewAnimationUtils.createCircularReveal(this, width / 2, height / 2, if (show) init else max, if (show) max else init)
            animator.exec {
                it.addListener(object : AnimatorListener() {
                    override fun onAnimationStart(animation: Animator?) {
                        if (show) {
                            visibility = View.VISIBLE
                        }
                    }

                    override fun onAnimationEnd(animation: Animator?) {
                        if (!show) {
                            visibility = View.INVISIBLE
                        }
                    }
                });
                it.start()
            }
        } else {
            ViewCompat.animate(this).alpha(if (show) 1f else 0f).setDuration(250).start()
        }
    }
}