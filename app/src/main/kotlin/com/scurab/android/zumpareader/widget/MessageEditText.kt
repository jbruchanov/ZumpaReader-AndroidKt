package com.scurab.android.zumpareader.widget

import android.content.Context
import android.content.ClipboardManager
import android.support.v7.appcompat.R
import android.support.v7.widget.AppCompatEditText
import android.text.*
import android.util.AttributeSet
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import java.lang.NullPointerException

/**
 * Created by JBruchanov on 23/03/2017.
 */
class MessageEditText @JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.editTextStyle) : AppCompatEditText(context, attrs, defStyleAttr) {

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (android.R.id.paste == id) {
            if (onInsertTextFromClipboard()) {
                return true
            }
        }
        return super.onTextContextMenuItem(id)
    }

    protected fun onInsertTextFromClipboard() : Boolean {
        if (text == null) {
            text = SpannableStringBuilder()
        }
        val mText : Editable = text ?: throw NullPointerException("Just assigned value and still null?")
        var min = 0
        var max = mText.length

        if (isFocused) {
            val selStart = selectionStart
            val selEnd = selectionEnd

            min = Math.max(0, Math.min(selStart, selEnd))
            max = Math.max(0, Math.max(selStart, selEnd))
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var result = false
        cm.primaryClip?.let { primaryClip ->
            var didFirst = false
            for (i in 0 until primaryClip.itemCount) {
                val clip = primaryClip.getItemAt(i)
                var paste: CharSequence?
                // Get an item as text and remove all spans by toString().
                val text = clip.coerceToText(context)
                paste = (text as? Spanned)?.toString() ?: text
                if (paste != null) {
                    var result = paste.toString()
                    val links = ZumpaSimpleParser.getLinks(result)
                    links.let {
                        for (link in it) {
                            result = result.replace(link, "<$link>")
                        }
                    }
                    paste = result
                }
                if (paste != null) {
                    if (!didFirst) {
                        Selection.setSelection(mText as Spannable, max)
                        (mText as Editable).replace(min, max, paste)
                        didFirst = true
                    } else {
                        (mText as Editable).insert(selectionEnd, "\n")
                        (mText as Editable).insert(selectionEnd, paste)
                    }
                    result = true
                }
            }
        }
        return result
    }
}