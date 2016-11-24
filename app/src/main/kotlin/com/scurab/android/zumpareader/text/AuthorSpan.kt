package com.scurab.android.zumpareader.text

import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.scurab.android.zumpareader.util.OutRef

/**
 * Created by JBruchanov on 04/01/2016.
 */
class AuthorSpan(val contextColor: Int) : ForegroundColorSpan(contextColor)

fun Editable.containsAuthor(text: String, range: OutRef<IntRange>? = null): Boolean {
    for (span in getSpans(0, length, AuthorSpan::class.java)) {
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        if (text == substring(start, end)) {
            if (range != null) {
                range.data = IntRange(start, end)
            }
            return true
        }
    }
    return false
}

fun Editable.appendReply(text: String, color: Int) {
    val start = Math.max(findLastIndex(), 0)
    insert(start, text)
    setSpan(AuthorSpan(color), start, start + text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

internal fun Spanned.findLastIndex(): Int {
    val spans = getSpans(0, length, AuthorSpan::class.java)
    var last = spans.lastOrNull()
    return if (last == null) -1 else getSpanEnd(last)
}