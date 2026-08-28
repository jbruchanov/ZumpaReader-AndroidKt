package com.scurab.android.zumpareader.text

import androidx.compose.ui.graphics.Color
import com.scurab.android.zumpareader.reader.Smiley
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnnotatedTextRendererTest {

    private val renderer = AnnotatedTextRenderer(
        responseColor = Color.Blue,
        ratingGoodColor = Color.Green,
        ratingBadColor = Color.Red,
    )

    @Test
    fun `plain text survives untouched`() {
        val rendered = renderer.body("nothing special here")

        assertEquals("nothing special here", rendered.text.text)
    }

    @Test
    fun `a smiley becomes its character and leaves the text around it alone`() {
        val rendered = renderer.body("ahoj :) jak je")

        assertEquals("ahoj ${Smiley.SMILEY.glyph} jak je", rendered.text.text)
    }

    /**
     * The off-by-one this pins would leave the smiley's last character behind as text, giving
     * `🙂)abc`.
     */
    @Test
    fun `text after a smiley is not duplicated or clipped`() {
        val rendered = renderer.body(":)abc")

        assertEquals("${Smiley.SMILEY.glyph}abc", rendered.text.text)
    }

    @Test
    fun `two smileys both become characters`() {
        val rendered = renderer.body(":) and ;)")

        assertEquals("${Smiley.SMILEY.glyph} and ${Smiley.WINK.glyph}", rendered.text.text)
    }

    @Test
    fun `a smiley inside a url is left alone`() {
        val rendered = renderer.body("http://x/a:)b")

        assertEquals("http://x/a:)b", rendered.text.text)
    }

    @Test
    fun `a url is styled but its text is kept`() {
        val rendered = renderer.body("see https://zunpa.cz/x ok")

        assertEquals("see https://zunpa.cz/x ok", rendered.text.text)
        assertTrue(rendered.text.spanStyles.isNotEmpty())
    }

    @Test
    fun `an author with no rating is plain`() {
        val rendered = renderer.author("honza", null)

        assertEquals("honza", rendered.text.text)
        assertTrue(rendered.text.spanStyles.isEmpty())
    }

    @Test
    fun `a positive rating is coloured good and a negative one bad`() {
        val good = renderer.author("honza", "+3")
        val bad = renderer.author("honza", "-2")

        assertEquals("honza +3", good.text.text)
        assertEquals(Color.Green, good.text.spanStyles.single().item.color)
        assertEquals(Color.Red, bad.text.spanStyles.single().item.color)
    }

    @Test
    fun `a non breaking space becomes a normal one`() {
        val rendered = renderer.body("a b")

        assertEquals("a b", rendered.text.text)
    }
}
