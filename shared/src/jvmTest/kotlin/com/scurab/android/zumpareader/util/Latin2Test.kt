package com.scurab.android.zumpareader.util

import java.net.URLEncoder
import java.nio.charset.Charset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The hand-rolled Latin-2 table exists because `java.nio.charset` does not survive the move off the
 * JVM. While the tests still run on the JVM the JDK's own charset is available as an oracle, so the
 * table is checked against it rather than against a second copy of itself. These are the tests to
 * keep - as `androidTest`, or as fixed byte-vector assertions - once the code moves to `commonMain`.
 */
class Latin2Test {

    private val jdk: Charset = Charset.forName("ISO-8859-2")

    @Test
    fun `every one of the 256 bytes decodes to what the jdk charset says`() {
        val bytes = ByteArray(256) { it.toByte() }
        assertEquals(String(bytes, jdk), bytes.decodeLatin2())
    }

    @Test
    fun `every character the table can encode maps back to its own byte`() {
        for (value in 0..255) {
            val byte = value.toByte()
            val char = byteArrayOf(byte).decodeLatin2().single()
            assertEquals(byte, char.latin2ByteOrNull(), "byte 0x%02X".format(value))
        }
    }

    @Test
    fun `characters outside latin-2 report themselves as unencodable`() {
        assertNull('€'.latin2ByteOrNull()) // euro sign
        assertNull('Ж'.latin2ByteOrNull()) // cyrillic
        assertNull('�'.latin2ByteOrNull())
    }

    @Test
    fun `czech text round trips`() {
        val text = "Příliš žluťoučký kůň"
        assertEquals(text, text.encodeLatin2().decodeLatin2())
    }

    @Test
    fun `percent encoding matches the url encoder it replaces`() {
        val samples = listOf(
            "hello",
            "a b",
            "&",
            "a+b",
            "Příliš žluťoučký kůň",
            "!~'()*-._",
            "a/b?c=d#e",
            " ",
            "line\nbreak",
        )
        for (sample in samples) {
            assertEquals(URLEncoder.encode(sample, jdk.name()), sample.percentEncodeLatin2(), sample)
        }
    }

    @Test
    fun `code points walk surrogate pairs as one unit`() {
        val emoji = "😀" // U+1F600
        assertEquals(0x1F600, emoji.codePointAtIndex(0))
        assertEquals(2, charCount(emoji.codePointAtIndex(0)))
        assertEquals(1, charCount('a'.code))
    }
}
