package com.scurab.android.zumpareader.reader

/**
 * The smileys the forum's plain-text markup can contain.
 *
 * This used to be `ZumpaSimpleParser.SmileRes.DATA`, a `Map<Int, Pattern>` keyed by `R.drawable`,
 * which put an Android resource id inside the parser. The drawable is a rendering concern, so the
 * mapping now lives in [com.scurab.android.zumpareader.text.AnnotatedTextRenderer] and the parser
 * only knows *which* smiley it is.
 */
enum class Smiley {
    HM,
    KISS,
    LOL,
    O_O,
    P,
    SAD,
    SMILEY,
    SPEECHLESS,
    WINK,
    ;

    companion object {
        /** The patterns verbatim from the original `SmileRes` static initialiser. */
        val PATTERNS: Map<Smiley, Regex> = mapOf(
            HM to Regex(":[-o]?[/\\\\]"),
            KISS to Regex(":[-o]?\\*+"),
            LOL to Regex(":[-o]?D+"),
            O_O to Regex("[oO]_[oO]"),
            P to Regex(":[-o]?[pP]"),
            SAD to Regex(":[-o]?\\(+"),
            SMILEY to Regex(":[-o]?[\\)\\]]+"),
            SPEECHLESS to Regex(":[-o]?\\|"),
            WINK to Regex(";[-o]?\\)+"),
        )
    }
}
