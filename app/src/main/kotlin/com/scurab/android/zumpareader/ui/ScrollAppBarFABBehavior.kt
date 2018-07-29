package com.scurab.android.zumpareader.ui

import android.content.Context
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * Created by JBruchanov on 25/11/2015.
 */
class ScrollAppBarFABBehavior(context: Context, attrs: AttributeSet) : CoordinatorLayout.Behavior<FloatingActionButton>(context, attrs) {

    private var childInitialOffset: Int = 0
    private var dependencyOffset: Int = 0

    override fun layoutDependsOn(parent: CoordinatorLayout?, child: FloatingActionButton?, dependency: View?): Boolean {
        return dependency is AppBarLayout
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: FloatingActionButton, dependency: View): Boolean {
        //Check if the view position has actually changed
        if (dependencyOffset != dependency.top) {
            dependencyOffset = dependency.top

            var margin = 0
            (child.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                margin = it.topMargin + it.bottomMargin
            }
            val offsetSpeed = (child.height + margin) / dependency.height.toFloat()
            val offset = (childInitialOffset - child.top - dependencyOffset + (dependency.height / 2))
            child.offsetTopAndBottom(offset)
            //Notify that we changed our attached child
            return true
        }

        return false
    }

    override fun onLayoutChild(parent: CoordinatorLayout?, child: FloatingActionButton?, layoutDirection: Int): Boolean {
        childInitialOffset = child?.top ?: 0
        return super.onLayoutChild(parent, child, layoutDirection)
    }
}