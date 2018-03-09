package com.scurab.android.zumpareader.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.model.Survey
import com.scurab.android.zumpareader.model.SurveyItem
import org.jetbrains.anko.find

/**
 * Created by JBruchanov on 13/01/2016.
 */
class SurveyView : FrameLayout {

    interface ItemClickListener {
        fun onItemClick(item: SurveyItem)
    }

    private val content by lazy { find<ViewGroup>(R.id.content) }
    private val surveyText by lazy { find<TextView>(R.id.survey_text) }
    private val buttonCount: Int get() {
        return content.childCount - 1
    }
    private val clickListenerInner: ((View) -> Unit) = { v -> dispatchButtonClick(v) }
    var surveyItemClickListener: ItemClickListener? = null

    private var _survey: Survey? = null
    var survey: Survey?
        get() = _survey
        set(value) {
            _survey = value
            onUpdateUI()
        }

    constructor(context: Context?) : this(context, null)

    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        inflate(context, R.layout.widget_survey, this)
    }

    private fun dispatchButtonClick(v: View) {
        surveyItemClickListener?.onItemClick(v.tag as SurveyItem)
    }

    protected fun onUpdateUI() {
        survey?.let {
            surveyText.text = "${it.question}\n${it.responses}"
            ensureButtons(it.items.size)
            for (i in 1..buttonCount) {
                val item = it.items[i - 1]
                val btn = content.getChildAt(i) as Button
                btn.text = "${item.text} (${item.percents}%)"
                btn.background.mutate()
                btn.background.level = item.percents * 100/* 10000 = 100% */
                btn.isSelected = item.voted
                btn.tag = item
                btn.setOnClickListener(clickListenerInner)
            }
        }
    }

    private fun ensureButtons(size: Int) {
        while (buttonCount/*title*/ != size) {
            val li = LayoutInflater.from(context)
            if (buttonCount < size) {
                li.inflate(R.layout.widget_survey_button, content, true)
            } else {
                content.removeViewAt(content.childCount - 1)
            }
        }
    }
}