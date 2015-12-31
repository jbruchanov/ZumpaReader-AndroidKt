package com.scurab.android.zumpareader.widget

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import com.pawegio.kandroid.find
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.util.obtainStyledColor
import com.scurab.android.zumpareader.util.wrapWithTint

/**
 * Created by JBruchanov on 31/12/2015.
 */
public class PostMessageView : FrameLayout {

    public val subject by lazy { find<EditText>(R.id.subject) }
    public val message by lazy { find<EditText>(R.id.message) }
    public val photo by lazy { find<ImageButton>(R.id.photo) }
    public val camera by lazy { find<ImageButton>(R.id.camera) }
    public val survey by lazy { find<ImageButton>(R.id.survey) }
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
        message.maxLines = Int.MAX_VALUE
        message.minHeight = resources.getDimensionPixelSize(R.dimen.new_message_edit_text_min_height)
        message.maxHeight = resources.getDimensionPixelSize(R.dimen.new_message_edit_text_min_height)
        message.gravity = Gravity.TOP or Gravity.LEFT
    }

    private fun initIcons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val color = context.obtainStyledColor(R.attr.contextColor)
            updateTint(photo, color)
            updateTint(camera, color)
            updateTint(survey, color)
            updateTint(sendButton, color)
        }
    }

    private fun updateTint(imageView: ImageView, color: Int) {
        imageView.setImageDrawable(imageView.drawable.wrapWithTint(color))
    }
}