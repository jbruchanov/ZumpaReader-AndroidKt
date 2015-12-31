package com.scurab.android.zumpareader.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import com.pawegio.kandroid.find
import com.scurab.android.zumpareader.R

/**
 * Created by JBruchanov on 31/12/2015.
 */
public class PostMessageView : FrameLayout {

    public val message by lazy { find<EditText>(R.id.message) }
    public val photo by lazy { find<ImageButton>(R.id.photo) }
    public val camera by lazy { find<ImageButton>(R.id.camera) }
    public val survey by lazy { find<ImageButton>(R.id.survey) }
    public val sendButton by lazy { find<ImageButton>(R.id.send) }

    constructor(context: Context?) : this(context, null)

    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr){
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        inflate(context, R.layout.widget_post_message, this)
    }
}