package com.scurab.android.zumpareader.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rules that are easy to break by adding a smiley: the patterns overlap heavily - `:o` is the
 * nose of half of them - and the tie between two patterns claiming the same character is settled by
 * nothing more than the declaration order of the enum.
 */
class SmileysTest {

    @Test
    fun `plain text is untouched`() {
        assertEquals("nothing here", Smileys.replaceIn("nothing here"))
    }

    @Test
    fun `a smiley becomes its character`() {
        assertEquals("ahoj ${Smiley.SMILEY.glyph}", Smileys.replaceIn("ahoj :)"))
    }

    @Test
    fun `text on both sides of a smiley survives`() {
        assertEquals("a${Smiley.SMILEY.glyph}b", Smileys.replaceIn("a:)b"))
    }

    @Test
    fun `several smileys in one message`() {
        assertEquals(
            "${Smiley.SMILEY.glyph} ${Smiley.WINK.glyph} ${Smiley.SAD.glyph}",
            Smileys.replaceIn(":) ;) :("),
        )
    }

    @Test
    fun `an excluded range keeps its punctuation`() {
        val url = "http://x/a:)b"

        assertEquals(url, Smileys.replaceIn(url, excluding = listOf(url.indices)))
    }

    /**
     * The originals are declared above everything added later, so a nose is still a nose. Without
     * that, `:o)` is a surprised face followed by a stray bracket.
     */
    @Test
    fun `a smiley with an o nose is one smiley and not two`() {
        assertEquals(Smiley.SMILEY.glyph, Smileys.replaceIn(":o)"))
        assertEquals(Smiley.SPEECHLESS.glyph, Smileys.replaceIn(":o|"))
        assertEquals(Smiley.LOL.glyph, Smileys.replaceIn(":oD"))
        assertEquals(Smiley.P.glyph, Smileys.replaceIn(":oP"))
    }

    @Test
    fun `a surprised face on its own still works`() {
        assertEquals(Smiley.SURPRISED.glyph, Smileys.replaceIn(":o"))
        assertEquals(Smiley.SURPRISED.glyph, Smileys.replaceIn(":-O"))
    }

    /** The longer smiley starts a character earlier, so the overlap rule picks it. */
    @Test
    fun `a face that begins before the one inside it wins`() {
        assertEquals(Smiley.ANGEL.glyph, Smileys.replaceIn("O:)"))
        assertEquals(Smiley.DEVIL.glyph, Smileys.replaceIn(">:)"))
        assertEquals(Smiley.ANGRY.glyph, Smileys.replaceIn(">:("))
        assertEquals(Smiley.CRY.glyph, Smileys.replaceIn(":'("))
        assertEquals(Smiley.LAUGH_TEARS.glyph, Smileys.replaceIn(":'D"))
    }

    @Test
    fun `the added faces are found`() {
        assertEquals(Smiley.COOL.glyph, Smileys.replaceIn("8-)"))
        assertEquals(Smiley.HEART.glyph, Smileys.replaceIn("<3"))
        assertEquals(Smiley.BROKEN_HEART.glyph, Smileys.replaceIn("</3"))
        assertEquals(Smiley.WINK_P.glyph, Smileys.replaceIn(";p"))
        assertEquals(Smiley.THUMBS_UP.glyph, Smileys.replaceIn("(y)"))
        assertEquals(Smiley.THUMBS_DOWN.glyph, Smileys.replaceIn("(N)"))
        assertEquals(Smiley.ANGRY.glyph, Smileys.replaceIn(":@"))
    }

    /**
     * A bare `8)` or `B)` is left alone on purpose - a numbered list is more likely than a smiley,
     * and a smiley that fires by mistake swallows the characters it replaced.
     */
    @Test
    fun `an enumeration is not a pair of sunglasses`() {
        assertEquals("b) neco", Smileys.replaceIn("b) neco"))
        assertEquals("(8) neco", Smileys.replaceIn("(8) neco"))
    }

    @Test
    fun `every smiley has a glyph and no two share one`() {
        val glyphs = Smiley.entries.map { it.glyph }

        assertTrue(glyphs.none { it.isEmpty() })
        assertEquals(glyphs.size, glyphs.distinct().size)
    }

    /** The renderers read [Smiley.PATTERNS] and rely on its order being the declaration order. */
    @Test
    fun `the pattern map is in declaration order and complete`() {
        assertEquals(Smiley.entries.toList(), Smiley.PATTERNS.keys.toList())
    }
}
