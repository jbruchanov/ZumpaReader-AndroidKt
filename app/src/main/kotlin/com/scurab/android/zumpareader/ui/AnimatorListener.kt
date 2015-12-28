package com.scurab.android.zumpareader.ui

import android.animation.Animator
import android.support.v4.view.ViewPropertyAnimatorListener
import android.view.View

/**
 * Created by JBruchanov on 25/11/2015.
 */
public open class AnimatorListener : Animator.AnimatorListener, ViewPropertyAnimatorListener {
    override fun onAnimationEnd(view: View?) {
        onAnimationEnd(view, null)
    }

    override fun onAnimationStart(view: View?) {
        onAnimationStart(view, null)
    }

    override fun onAnimationCancel(view: View?) {
    }

    override fun onAnimationEnd(animation: Animator?) {
        onAnimationEnd(null, animation)
    }

    override fun onAnimationStart(animation: Animator?) {
        onAnimationStart(null, animation)
    }

    override fun onAnimationRepeat(animation: Animator?) {
    }

    override fun onAnimationCancel(animation: Animator?) {

    }

    open fun onAnimationEnd(view: View?, animation: Animator?) {

    }

    open fun onAnimationStart(view: View?, animation: Animator?) {

    }
}