package com.scurab.android.zumpareader.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.view.ViewCompat
import android.support.v7.appcompat.R
import android.util.AttributeSet
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.util.ZumpaPrefs
import com.scurab.android.zumpareader.util.exec

/**
 * Created by JBruchanov on 25/11/2015.
 */
public class QuickHideBehavior : CoordinatorLayout.Behavior<FloatingActionButton> {

    private val DIRECTION_UP = 1;
    private val DIRECTION_DOWN = -1;

    private var scrollingDirection = 0
    private var scrollTrigger = 0;
    private var scrollDistance = 0
    private var scrollThreshold = 0;
    private var animator : Animator? = null;
    private var _enabled = true
    public var enabled : Boolean
        get() = _enabled
        set(value) {
            _enabled = value
            scrollDistance = 0
            scrollTrigger = DIRECTION_DOWN
        }
    private val zumpaPrefs : ZumpaPrefs

    //Required to attach behavior via XML
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        val a = context.getTheme().obtainStyledAttributes(intArrayOf(R.attr.actionBarSize));
        try {
            zumpaPrefs = (context.applicationContext as ZumpaReaderApp).zumpaPrefs
        } catch(e: ClassCastException) {//preview
            zumpaPrefs = ZumpaPrefs(context)
        }
        //Use half the standard action bar height
        scrollThreshold = a.getDimensionPixelSize(0, 0) / 2;
        a.recycle();
    }

    override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout?, child: FloatingActionButton?, directTargetChild: View?, target: View?, nestedScrollAxes: Int): Boolean {
        return (nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout?, child: FloatingActionButton?, target: View?, dx: Int, dy: Int, consumed: IntArray?) {
        if (dy > 0 && scrollingDirection != DIRECTION_UP) {
            scrollingDirection = DIRECTION_UP;
            scrollDistance = 0;
        } else if (dy < 0 && scrollingDirection != DIRECTION_DOWN) {
            scrollingDirection = DIRECTION_DOWN;
            scrollDistance = 0;
        }
    }

    override fun onNestedScroll(coordinatorLayout: CoordinatorLayout,
                                child: FloatingActionButton,
                                target: View,
                                dxConsumed: Int, dyConsumed: Int,
                                dxUnconsumed: Int, dyUnconsumed: Int) {

        if (!enabled || !zumpaPrefs.isLoggedInNotOffline) {
            return;
        }
        //Consumed distance is the actual distance traveled by the scrolling view
        scrollDistance += dyConsumed;
        if (scrollDistance > scrollThreshold
                && scrollTrigger != DIRECTION_UP) {
            //Hide the target view
            scrollTrigger = DIRECTION_UP;
            restartAnimator(child, false, getTargetHideValue(coordinatorLayout, child));
        } else if (scrollDistance < -scrollThreshold
                && scrollTrigger != DIRECTION_DOWN) {
            //Return the target view
            scrollTrigger = DIRECTION_DOWN;
            restartAnimator(child, true, 0f);
        }
    }

    override fun onNestedFling(coordinatorLayout: CoordinatorLayout,
                               child: FloatingActionButton,
                               target: View,
                               velocityX: Float, velocityY: Float,
                               consumed: Boolean): Boolean {
        if (!enabled || !zumpaPrefs.isLoggedInNotOffline) {
            return false;
        }
        if (consumed) {
            if (velocityY > 0 && scrollTrigger != DIRECTION_UP) {
                scrollTrigger = DIRECTION_UP;
                restartAnimator(child, false, getTargetHideValue(coordinatorLayout, child));
            } else if (velocityY < 0 && scrollTrigger != DIRECTION_DOWN) {
                scrollTrigger = DIRECTION_DOWN;
                restartAnimator(child, true, 0f);
            }
        }

        return false;
    }

    //Helper to trigger hide/show animation
    private fun restartAnimator(target: View, show: Boolean?, value: Float?) {
        animator.exec {
            it.cancel()
            animator = null
        }

        if (show != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            var init = 0f
            var max = Math.max(target.width, target.height).toFloat()
            animator = ViewAnimationUtils.createCircularReveal(target, target.width / 2, target.height / 2, if (show) init else max, if (show) max else init)
            animator.exec {
                it.addListener(object : AnimatorListener() {
                    override fun onAnimationStart(animation: Animator?) {
                        if (show) {
                            target.visibility = View.VISIBLE
                        }
                    }

                    override fun onAnimationEnd(animation: Animator?) {
                        if (!show) {
                            target.visibility = View.INVISIBLE
                        }
                    }
                });
                it.start()
            }
        } else if (value != null) {
            animator = ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, value)
                    .setDuration(250)
                    .apply {
                        start()
                    }
        }
    }

    private fun getTargetHideValue(parent: ViewGroup, target: View): Float {
        var value = when (target) {
            is AppBarLayout -> -target.height
            is FloatingActionButton -> parent.height - target.top
            else -> 0
        }
        return value.toFloat()
    }
}