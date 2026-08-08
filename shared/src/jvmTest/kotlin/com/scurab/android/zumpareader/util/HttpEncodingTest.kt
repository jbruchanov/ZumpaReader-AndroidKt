package com.scurab.android.zumpareader.util

import kotlin.test.Test
import kotlin.test.assertEquals
import java.net.URLDecoder

/**
 * The forum's charset is ISO-8859-2. What matters is not the exact escape but that the value
 * survives the round trip the backend does - percent-decode with the legacy charset - with the
 * characters still identifiable.
 */
class HttpEncodingTest {

    private fun String.decoded(): String = URLDecoder.decode(this, "ISO-8859-2")

    @Test
    fun `ascii is unchanged`() {
        assertEquals("hello", "hello".encodeHttp())
    }

    @Test
    fun `czech diacritics survive because the charset can carry them`() {
        val text = "Příliš žluťoučký kůň úpěl ďábelské ódy"
        assertEquals(text, text.encodeHttp().decoded())
    }

    @Test
    fun `an emoji becomes a numeric character reference instead of a question mark`() {
        assertEquals("&#128512;", "😀".encodeHttp().decoded())
    }

    @Test
    fun `a surrogate pair produces one reference and not two`() {
        assertEquals(1, Regex("&#\\d+;").findAll("😀".encodeHttp().decoded()).count())
    }

    @Test
    fun `only the unencodable characters are replaced`() {
        val encoded = "ahoj 😀 světe".encodeHttp().decoded()
        assertEquals("ahoj &#128512; světe", encoded)
    }

    @Test
    fun `characters outside latin-2 but not emoji are escaped too`() {
        //cyrillic - representable in neither latin-2 nor the old question-mark output
        assertEquals("&#1055;", "П".encodeHttp().decoded())
    }

    @Test
    fun `spaces still become plus signs so the form body is unchanged for plain text`() {
        assertEquals("a+b", "a b".encodeHttp())
    }

    @Test
    fun `an ampersand the user typed is percent encoded and not treated as markup`() {
        assertEquals("%26", "&".encodeHttp())
    }
}
