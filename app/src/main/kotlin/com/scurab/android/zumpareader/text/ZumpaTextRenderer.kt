package com.scurab.android.zumpareader.text

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.util.LruCache
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser

/**
 * The single place that turns zumpa's markup into something renderable.
 *
 * [T] is [CharSequence] for the view system ([SpannedTextRenderer]) and will be
 * `androidx.compose.ui.text.AnnotatedString` for the compose implementation. Every call site that
 * used to reach for `ZumpaSimpleParser.parseBody` or the `styled*` caches on the model classes goes
 * through here, so swapping the rendering out is one new class and no screen changes.
 *
 * Implementations need a *themed* context - `ZumpaSimpleParser.parseBody` resolves
 * `R.attr.contextColorText2` off the theme, so an application context resolves the wrong value.
 * That is why rendering lives at the ui boundary and not inside a ViewModel.
 */
interface ZumpaTextRenderer<out T : Any> {
    /** A message body, with smileys, links and quoted-response highlighting. */
    fun body(markup: String): T

    /** A thread subject as it appears in a list row. */
    fun subject(markup: String): T

    /** A thread subject as it appears in the toolbar - icons align to the baseline there. */
    fun title(markup: String): T

    /** An author name with the optional `+3` / `-2` rating appended in the rating colour. */
    fun author(name: String, rating: String?): T
}

class SpannedTextRenderer(private val themedContext: Context) : ZumpaTextRenderer<CharSequence> {

    private val bodies = LruCache<String, CharSequence>(CACHE_SIZE)
    private val subjects = LruCache<String, CharSequence>(CACHE_SIZE)
    private val titles = LruCache<String, CharSequence>(CACHE_SIZE)
    private val authors = LruCache<String, CharSequence>(CACHE_SIZE)

    private val ratingGood by lazy { themedContext.resources.getColor(R.color.rating_good) }
    private val ratingBad by lazy { themedContext.resources.getColor(R.color.rating_bad) }

    override fun body(markup: String): CharSequence = bodies.getOrPut(markup) {
        ZumpaSimpleParser.parseBody(markup, themedContext)
    }

    override fun subject(markup: String): CharSequence = subjects.getOrPut(markup) {
        ZumpaSimpleParser.parseBody(markup, themedContext)
    }

    override fun title(markup: String): CharSequence = titles.getOrPut(markup) {
        ZumpaSimpleParser.parseBody(markup, themedContext, ImageSpan.ALIGN_BASELINE)
    }

    override fun author(name: String, rating: String?): CharSequence {
        if (rating.isNullOrEmpty()) {
            return name
        }
        return authors.getOrPut("$name $rating") {
            val text = SpannableString("$name $rating")
            val color = if (rating[0] == '+') ratingGood else ratingBad
            text.setSpan(
                ForegroundColorSpan(color),
                text.length - rating.length,
                text.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
            text
        }
    }

    private inline fun <K : Any, V : Any> LruCache<K, V>.getOrPut(key: K, produce: () -> V): V {
        return get(key) ?: produce().also { put(key, it) }
    }

    private companion object {
        const val CACHE_SIZE = 512
    }
}
