package com.scurab.android.zumpareader.widget

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import com.pawegio.kandroid.find
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.util.execOn
import com.scurab.android.zumpareader.util.obtainStyledColor
import com.scurab.android.zumpareader.util.setImageTint

/**
 * Created by JBruchanov on 31/12/2015.
 */
public class PostMessageView : FrameLayout {

    public val subject by lazy(LazyThreadSafetyMode.NONE) { find<EditText>(R.id.subject) }
    public val message by lazy { find<EditText>(R.id.message) }
    public val photo by lazy { find<ImageButton>(R.id.photo) }
    public val camera by lazy { find<ImageButton>(R.id.camera) }
    public val sendButton by lazy { find<ImageButton>(R.id.send) }

    constructor(context: Context?) : this(context, null)

    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        inflate(context, R.layout.widget_post_message, this)
        initIcons()
    }

    public fun setUIForNewMessage() {
        subject.visibility = View.VISIBLE
        message.maxLines = Integer.MAX_VALUE
        message.gravity = Gravity.TOP or Gravity.LEFT
        (message.layoutParams as LinearLayout.LayoutParams).execOn {
            weight = 1f
            height = 0
            requestLayout()
        }
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
    }

    private fun initIcons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val color = context.obtainStyledColor(R.attr.contextColor)
            photo.setImageTint(color)
            camera.setImageTint(color)
            sendButton.setImageTint(color)
        }
    }
}