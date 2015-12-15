package com.scurab.android.zumpareader.drawable

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable

/**
 * Created by JBruchanov on 15/12/2015.
 */

class SimpleProgressDrawable : Drawable {

    private val paint: Paint
    private val dispSize : Int
    private val rect : RectF
    private var radius : Float = 100f
    private var rotation : Float = 0f
    private var rotation2 : Float = 0f

    constructor(res: Resources) : super() {
        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.strokeWidth = 10f
        paint.style = Paint.Style.STROKE
//        paint.color = Color.RED
        dispSize = Math.min(res.displayMetrics.widthPixels, res.displayMetrics.heightPixels)
        paint.setShader(SweepGradient (radius / 2, radius / 2, Color.argb(127, 255, 0, 0), Color.TRANSPARENT))
        rect = RectF()
        rect.set(0f, 0f, 100f, 100f);
    }

    override fun getOpacity(): Int {
        return paint.alpha
    }

    override fun draw(canvas: Canvas) {
        val cx = (bounds.width() / 2) - radius
        val cy = (bounds.height() / 2) - radius

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotation, radius / 2, radius / 2)
        canvas.drawArc(rect, -90f, 360f, false, paint);
        canvas.restore()

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(180 - rotation, radius / 2, radius / 2)
        canvas.drawArc(rect, 180f, 360f, false, paint);
        canvas.restore()

        rotation += 5
        invalidateSelf()
    }

    override fun getIntrinsicHeight(): Int {
        return 200
    }

    override fun getIntrinsicWidth(): Int {
        return dispSize
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.setColorFilter(colorFilter)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }
}