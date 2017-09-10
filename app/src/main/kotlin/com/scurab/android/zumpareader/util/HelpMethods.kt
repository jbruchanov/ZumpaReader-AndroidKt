package com.scurab.android.zumpareader.util

import android.content.Context
import android.net.Uri
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
import android.content.Context.ACTIVITY_SERVICE
import android.app.ActivityManager


/**
 * Created by JBruchanov on 10/09/2017.
 */
fun scaledImageRequest(url: String, context: Context): ImageRequest {
    return ImageRequestBuilder
            .newBuilderWithSource(Uri.parse(url))
            .setResizeOptions(maxResolution(context))
            .build()
}

fun maxResolution(context: Context): ResizeOptions? {
    val dm = context.resources.displayMetrics
    val memClass = (context.getSystemService(ACTIVITY_SERVICE) as ActivityManager).memoryClass
    var coef: Float
    when (memClass) {
        in Integer.MIN_VALUE..128 -> coef = 0.33f
        in 129..256 -> coef = 0.66f
        else -> coef = 1f
    }
    val w = (dm.widthPixels * coef).toInt()
    val h = (dm.heightPixels * coef).toInt()
    return ResizeOptions(w, h)
}