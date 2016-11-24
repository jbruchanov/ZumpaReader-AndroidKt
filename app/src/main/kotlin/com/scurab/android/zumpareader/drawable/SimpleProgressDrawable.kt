package com.scurab.android.zumpareader.drawable

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.TypedValue
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.util.obtainStyledColor

/**
 * Created by JBruchanov on 15/12/2015.
 */

class SimpleProgressDrawable : Drawable {

    private val paint: Paint
    private val dispSize: Int
    private val rect: RectF
    private var diam: Float
    private var radius: Float
    private var rotation: Float = 0f
    private var rotation2: Float = 0f

    constructor(context: Context) : super() {
        val res = context.resources
        val dm = res.displayMetrics
        diam = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50f, dm)
        radius = diam / 2
        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, dm)
        paint.style = Paint.Style.STROKE
        dispSize = Math.min(dm.widthPixels, dm.heightPixels)
        paint.shader = SweepGradient (radius, radius, context.obtainStyledColor(R.attr.contextColor50p), Color.TRANSPARENT)
        rect = RectF()
        rect.set(0f, 0f, diam, diam)
    }

    override fun getOpacity(): Int {
        return paint.alpha
    }

    override fun draw(canvas: Canvas) {
        val cx = (bounds.width() / 2) - radius
        val cy = (bounds.height() / 2) - radius

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotation, radius, radius)
        canvas.drawArc(rect, -90f, 360f, false, paint)
        canvas.restore()

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(180 - rotation, radius, radius)
        canvas.drawArc(rect, 180f, 360f, false, paint)
        canvas.restore()

        rotation += 5
        invalidateSelf()
    }

    override fun getIntrinsicHeight(): Int {
        return (0.5f + diam).toInt()
    }

    override fun getIntrinsicWidth(): Int {
        return dispSize
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }
}