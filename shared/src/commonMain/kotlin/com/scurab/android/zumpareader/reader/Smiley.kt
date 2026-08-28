package com.scurab.android.zumpareader.reader

/**
 * The smileys the forum's plain-text markup can contain, and the character each one renders as.
 *
 * This used to be `ZumpaSimpleParser.SmileRes.DATA`, a `Map<Int, Pattern>` keyed by `R.drawable`,
 * which put an Android resource id inside the parser. Then the drawable mapping moved out to
 * `AnnotatedTextRenderer` and this held only the patterns.
 *
 * Now it holds [glyph] too, and there are no drawables left: a smiley is a character the text font
 * draws, not a small bitmap stretched to the line height. That is also what gives the desktop
 * smileys at all - it cannot reach `:appAndroid`'s `res/drawable`, and an inline image needs a
 * renderer on every platform where a character needs none.
 *
 * **Declaration order is priority.** Two patterns can claim the same text and the first one wins -
 * see [Smileys.matches] - so the nine the forum has always had are declared first, and nothing
 * added below them can change how an existing message reads.
 */
enum class Smiley(val glyph: String, val pattern: Regex) {
    //region the original nine - patterns verbatim from `SmileRes`
    HM("😕", Regex(":[-o]?[/\\\\]")),
    KISS("😘", Regex(":[-o]?\\*+")),
    LOL("😃", Regex(":[-o]?D+")),
    O_O("😳", Regex("[oO]_[oO]")),
    P("😛", Regex(":[-o]?[pP]")),
    SAD("🙁", Regex(":[-o]?\\(+")),
    SMILEY("🙂", Regex(":[-o]?[\\)\\]]+")),
    SPEECHLESS("😐", Regex(":[-o]?\\|")),
    WINK("😉", Regex(";[-o]?\\)+")),
    //endregion

    //region added - the ones that were being typed all along and staying as punctuation
    /**
     * `O:)`, `o:-)`. Its position in this list happens not to matter: it starts a character earlier
     * than the [SMILEY] hiding inside it, so the overlap rule picks it without help from the order.
     */
    ANGEL("😇", Regex("[oO]:[-o]?\\)+")),
    DEVIL("😈", Regex(">:[-o]?\\)+")),
    ANGRY("😠", Regex(">:[-o]?\\(+|:[-o]?@")),
    CRY("😢", Regex(":'\\(+")),
    LAUGH_TEARS("😂", Regex(":'D+")),
    /**
     * The nose is required. `8)` and `B)` on their own are as likely to be a numbered list or a
     * bracket after a figure, and a smiley that fires by mistake eats the text it replaced.
     */
    COOL("😎", Regex("[8B]-\\)+")),
    HEART("❤️", Regex("<3+")),
    BROKEN_HEART("💔", Regex("</3")),
    /**
     * `:o`, `:-O`.
     *
     * This is the one the order above exists for. `:o` is also the *nose* of half the originals -
     * `:o)`, `:o|`, `:oD` - so it matches at the same character they do, and being declared below
     * them is the whole of what keeps `:o)` a smiley rather than a surprised face and a bracket.
     */
    SURPRISED("😮", Regex(":-?[oO]")),
    WINK_P("😜", Regex(";[-o]?[pP]")),
    THUMBS_UP("👍", Regex("\\([yY]\\)")),
    THUMBS_DOWN("👎", Regex("\\([nN]\\)")),
    //endregion
    ;

    companion object {
        /**
         * Kept as a map because that is how the renderers have always read this, and derived from
         * [entries] so the iteration order is the declaration order by construction rather than by
         * whoever edits the literal next.
         */
        val PATTERNS: Map<Smiley, Regex> = entries.associateWith { it.pattern }
    }
}

/** One smiley found in a message, and where in it. */
data class SmileyMatch(val smiley: Smiley, val range: IntRange)

/**
 * Finding smileys in a message, in one place, because both apps have to find the same ones.
 *
 * The Android renderer walks the ranges itself - it has style spans to keep in step with them - and
 * the desktop, which has no renderer, asks for [replaceIn] and is done.
 */
object Smileys {

    /**
     * Every smiley in [source], left to right, overlaps resolved first-come-first-served.
     *
     * @param excluding ranges no smiley may overlap - urls, in practice, so `http://x/a:)b` stays a
     * url rather than losing two characters out of its middle.
     */
    fun matches(source: String, excluding: List<IntRange> = emptyList()): List<SmileyMatch> {
        val found = ArrayList<SmileyMatch>()
        Smiley.PATTERNS.forEach { (smiley, pattern) ->
            pattern.findAll(source).forEach { match ->
                if (excluding.none { it.overlaps(match.range) }) {
                    found += SmileyMatch(smiley, match.range)
                }
            }
        }
        //a stable sort, so two patterns claiming the same starting character resolve by the
        //declaration order of the enum rather than by a map's iteration order
        return found.sortedBy { it.range.first }
            .fold(ArrayList<SmileyMatch>()) { acc, smiley ->
                if (acc.none { it.range.overlaps(smiley.range) }) acc += smiley
                acc
            }
    }

    /** [source] with every smiley replaced by its character. */
    fun replaceIn(source: String, excluding: List<IntRange> = emptyList()): String {
        val found = matches(source, excluding)
        if (found.isEmpty()) return source
        val out = StringBuilder(source.length)
        var index = 0
        found.forEach { match ->
            if (match.range.first > index) {
                out.append(source, index, match.range.first)
            }
            out.append(match.smiley.glyph)
            index = match.range.last + 1
        }
        if (index < source.length) {
            out.append(source, index, source.length)
        }
        return out.toString()
    }
}

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last
