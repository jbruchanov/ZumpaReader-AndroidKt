package com.scurab.android.zumpareader.text

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import com.scurab.android.zumpareader.reader.Smileys
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser

/**
 * What [AnnotatedTextRenderer] produces.
 *
 * A plain wrapper around an [AnnotatedString] since the smileys became characters. It used to carry
 * an `inlineContent` map beside the text, because each smiley was an image occupying a placeholder
 * in the string - see [AnnotatedTextRenderer].
 */
@Immutable
data class RenderedText(val text: AnnotatedString) {
    companion object {
        val Empty = RenderedText(AnnotatedString(""))
    }
}

/**
 * The single place that turns zumpa's markup into something renderable.
 *
 * A port of `ZumpaSimpleParser.parseBody`, reusing its patterns so the two cannot drift: urls get
 * the same half-size monospace treatment, quoted responses the same colour, and the smileys come
 * from `:shared` so the desktop finds exactly the same ones.
 *
 * Smileys are **characters**, drawn by whatever font the text is in. They used to be `emoji_*`
 * drawables in an `InlineTextContent` placeholder, which meant a themed resource lookup at draw
 * time, a placeholder sized in `em` that a text-selection or copy had to see through, and nine
 * bitmaps that no other platform could reach. `:)` is now simply `🙂` in the string.
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
            return RenderedText(AnnotatedString(name))
        }
        val text = buildAnnotatedString {
            append(name)
            append(" ")
            withStyleOf(if (rating[0] == '+') ratingGoodColor else ratingBadColor) { append(rating) }
        }
        return RenderedText(text)
    }

    private fun render(markup: String): RenderedText {
        val source = markup.replace(NBSP, ' ')

        val links = ZumpaSimpleParser.URL_PATTERN2.ranges(source)
        val smileys = Smileys.matches(source, excluding = links)
        val responses = ZumpaSimpleParser.RESPONSE_PATTERN
            .ranges(source, group = 1)
            .filterNot { range -> links.any { it.overlaps(range) } }

        val text = buildAnnotatedString {
            var index = 0
            //smileys replace their text, so they drive the walk; everything else is a style span
            for (smiley in smileys) {
                if (smiley.range.first > index) {
                    appendStyled(source, index, smiley.range.first, links, responses)
                }
                append(smiley.smiley.glyph)
                index = smiley.range.last + 1
            }
            if (index < source.length) {
                appendStyled(source, index, source.length, links, responses)
            }
        }
        return RenderedText(text)
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

    private inline fun AnnotatedString.Builder.withStyleOf(color: Color, block: () -> Unit) {
        val start = length
        block()
        addStyle(SpanStyle(color = color), start, length)
    }

    private companion object {
        /** Spelled as an escape: as a literal it is a space that only a hex editor can tell apart. */
        const val NBSP = '\u00A0'

        /** `RelativeSizeSpan(0.5f)` + `TypefaceSpan("monospace")` in the span version. */
        val LINK_STYLE = SpanStyle(
            fontSize = 0.5.em,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

private fun Regex.ranges(input: String, group: Int = 0): List<IntRange> =
    findAll(input).mapNotNull { it.groups[group]?.range }.toList()

private fun List<IntRange>.clipTo(from: Int, to: Int): List<Pair<Int, Int>> = mapNotNull { range ->
    val start = maxOf(range.first, from)
    val end = minOf(range.last + 1, to)
    if (start < end) start to end else null
}
