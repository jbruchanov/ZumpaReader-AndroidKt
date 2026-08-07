package com.scurab.android.zumpareader.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser

/**
 * What [AnnotatedTextRenderer] produces. An [AnnotatedString] alone is not enough - the smileys are
 * inline images, so the placeholders they occupy come with it.
 */
@Immutable
data class RenderedText(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
) {
    companion object {
        val Empty = RenderedText(AnnotatedString(""), emptyMap())
    }
}

/**
 * The single place that turns zumpa's markup into something renderable.
 *
 * A port of `ZumpaSimpleParser.parseBody`, reusing its patterns so the two cannot drift: urls get
 * the same half-size monospace treatment, quoted responses the same colour, and the smiley table is
 * the parser's own. Unlike the Spanned version it needs no themed Context - the colours are handed
 * in from [com.scurab.android.zumpareader.ui.compose.theme.AppTheme] and the drawables are resolved
 * by `painterResource` inside the inline content, at draw time - unlike the `Spanned` renderer this
 * replaced, which needed a themed Context and so could never live in a ViewModel.
 */
class AnnotatedTextRenderer(
    private val responseColor: Color,
    private val ratingGoodColor: Color,
    private val ratingBadColor: Color,
) {

    /** A message body, with smileys, links and quoted-response highlighting. */
    fun body(markup: String): RenderedText = render(markup)

    /** A thread subject as it appears in a list row. */
    fun subject(markup: String): RenderedText = render(markup)

    /** A thread subject as it appears in the toolbar. */
    fun title(markup: String): RenderedText = render(markup)

    /** An author name with the optional `+3` / `-2` rating appended in the rating colour. */
    fun author(name: String, rating: String?): RenderedText {
        if (rating.isNullOrEmpty()) {
            return RenderedText(AnnotatedString(name), emptyMap())
        }
        val text = buildAnnotatedString {
            append(name)
            append(" ")
            withStyleOf(if (rating[0] == '+') ratingGoodColor else ratingBadColor) { append(rating) }
        }
        return RenderedText(text, emptyMap())
    }

    private fun render(markup: String): RenderedText {
        val source = markup.replace(NBSP, ' ')
        val inline = LinkedHashMap<String, InlineTextContent>()

        val links = ZumpaSimpleParser.URL_PATTERN2.matcher(source).ranges()
        val smileys = smileyRanges(source, links)
        val responses = ZumpaSimpleParser.RESPONSE_PATTERN.matcher(source)
            .ranges(group = 1)
            .filterNot { range -> links.any { it.overlaps(range) } }

        val text = buildAnnotatedString {
            var index = 0
            //smileys replace their text, so they drive the walk; everything else is a style span
            val ordered = smileys.sortedBy { it.range.first }
            for (smiley in ordered) {
                if (smiley.range.first > index) {
                    appendStyled(source, index, smiley.range.first, links, responses)
                }
                val id = "smiley:${smiley.drawableRes}:${smiley.range.first}"
                inline[id] = smileyContent(smiley.drawableRes)
                appendInlineContent(id, source.substring(smiley.range))
                index = smiley.range.last + 1
            }
            if (index < source.length) {
                appendStyled(source, index, source.length, links, responses)
            }
        }
        return RenderedText(text, inline)
    }

    private fun AnnotatedString.Builder.appendStyled(
        source: String,
        from: Int,
        to: Int,
        links: List<IntRange>,
        responses: List<IntRange>,
    ) {
        val start = length
        append(source.substring(from, to))
        links.clipTo(from, to).forEach { (s, e) ->
            addStyle(LINK_STYLE, start + (s - from), start + (e - from))
        }
        responses.clipTo(from, to).forEach { (s, e) ->
            addStyle(SpanStyle(color = responseColor), start + (s - from), start + (e - from))
        }
    }

    private fun smileyRanges(source: String, links: List<IntRange>): List<Smiley> {
        val found = ArrayList<Smiley>()
        ZumpaSimpleParser.SmileRes.DATA.forEach { (drawable, pattern) ->
            val matcher = pattern.matcher(source)
            while (matcher.find()) {
                val range = matcher.start() until matcher.end()
                if (links.none { it.overlaps(range) }) {
                    found += Smiley(drawable, range)
                }
            }
        }
        //two patterns can claim the same text, first wins, exactly as the span version behaved
        return found.sortedBy { it.range.first }
            .fold(ArrayList<Smiley>()) { acc, smiley ->
                if (acc.none { it.range.overlaps(smiley.range) }) acc += smiley
                acc
            }
    }

    private fun smileyContent(drawableRes: Int) = InlineTextContent(
        placeholder = Placeholder(
            width = SMILEY_SIZE,
            height = SMILEY_SIZE,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        ),
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }

    private inline fun AnnotatedString.Builder.withStyleOf(color: Color, block: () -> Unit) {
        val start = length
        block()
        addStyle(SpanStyle(color = color), start, length)
    }

    private data class Smiley(val drawableRes: Int, val range: IntRange)

    private companion object {
        const val NBSP = ' '
        val SMILEY_SIZE: TextUnit = 1.4.em

        /** `RelativeSizeSpan(0.5f)` + `TypefaceSpan("monospace")` in the span version. */
        val LINK_STYLE = SpanStyle(
            fontSize = 0.5.em,
            fontFamily = FontFamily.Monospace,
            textDecoration = TextDecoration.Underline,
        )
    }
}

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

private fun java.util.regex.Matcher.ranges(group: Int = 0): List<IntRange> {
    val result = ArrayList<IntRange>()
    while (find()) {
        result += start(group) until end(group)
    }
    return result
}

private fun List<IntRange>.clipTo(from: Int, to: Int): List<Pair<Int, Int>> = mapNotNull { range ->
    val start = maxOf(range.first, from)
    val end = minOf(range.last + 1, to)
    if (start < end) start to end else null
}

private fun String.substring(range: IntRange): String =
    substring(range.first, minOf(range.last + 1, length))
