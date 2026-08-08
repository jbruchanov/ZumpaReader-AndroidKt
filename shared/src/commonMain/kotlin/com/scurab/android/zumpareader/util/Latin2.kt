package com.scurab.android.zumpareader.util

/**
 * ISO-8859-2 (Latin-2), the charset the forum is served in - see `ZR.Constants.ENCODING`.
 *
 * Hand-rolled on purpose. `java.nio.charset` is JVM-only, and Kotlin/Native and Kotlin/JS have no
 * general charset support at all - `Charset.forName("ISO-8859-2")` is not something that can be
 * relied on outside the JVM. Latin-2 is a fixed 256-entry mapping, so the table *is* the port.
 *
 * Bytes `0x00..0x9F` map to the code point of the same value. Only the top 96 differ, and that is
 * the whole table below. `Latin2Test` checks every byte against the JDK charset, so the values are
 * verified rather than trusted.
 */
private const val HIGH_START = 0xA0

// Code points, not literal characters: 0xA0 is a no-break space and 0xAD a soft hyphen, both
// invisible in a source file and both easy to mangle in an editor or a diff. Generated from the
// JDK's own ISO-8859-2 charset, one row per 8 bytes starting at 0xA0.
private val LATIN2_HIGH: CharArray = intArrayOf(
    0x00A0, 0x0104, 0x02D8, 0x0141, 0x00A4, 0x013D, 0x015A, 0x00A7, // A0
    0x00A8, 0x0160, 0x015E, 0x0164, 0x0179, 0x00AD, 0x017D, 0x017B, // A8
    0x00B0, 0x0105, 0x02DB, 0x0142, 0x00B4, 0x013E, 0x015B, 0x02C7, // B0
    0x00B8, 0x0161, 0x015F, 0x0165, 0x017A, 0x02DD, 0x017E, 0x017C, // B8
    0x0154, 0x00C1, 0x00C2, 0x0102, 0x00C4, 0x0139, 0x0106, 0x00C7, // C0
    0x010C, 0x00C9, 0x0118, 0x00CB, 0x011A, 0x00CD, 0x00CE, 0x010E, // C8
    0x0110, 0x0143, 0x0147, 0x00D3, 0x00D4, 0x0150, 0x00D6, 0x00D7, // D0
    0x0158, 0x016E, 0x00DA, 0x0170, 0x00DC, 0x00DD, 0x0162, 0x00DF, // D8
    0x0155, 0x00E1, 0x00E2, 0x0103, 0x00E4, 0x013A, 0x0107, 0x00E7, // E0
    0x010D, 0x00E9, 0x0119, 0x00EB, 0x011B, 0x00ED, 0x00EE, 0x010F, // E8
    0x0111, 0x0144, 0x0148, 0x00F3, 0x00F4, 0x0151, 0x00F6, 0x00F7, // F0
    0x0159, 0x016F, 0x00FA, 0x0171, 0x00FC, 0x00FD, 0x0163, 0x02D9, // F8
).let { points -> CharArray(points.size) { points[it].toChar() } }

/** Reverse of [LATIN2_HIGH]. Only the 96 high entries - below [HIGH_START] the byte is the char. */
private val LATIN2_REVERSE: Map<Char, Byte> =
    LATIN2_HIGH.withIndex().associate { (i, c) -> c to (HIGH_START + i).toByte() }

/** Decodes Latin-2 bytes. Every byte is a valid Latin-2 character, so this cannot fail. */
fun ByteArray.decodeLatin2(): String {
    val out = StringBuilder(size)
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        out.append(if (value < HIGH_START) value.toChar() else LATIN2_HIGH[value - HIGH_START])
    }
    return out.toString()
}

/** The Latin-2 byte for [this], or `null` when the charset cannot represent it. */
fun Char.latin2ByteOrNull(): Byte? = when {
    code < HIGH_START -> code.toByte()
    else -> LATIN2_REVERSE[this]
}

/** Whether every character of [this] survives a round trip through Latin-2. */
fun String.canEncodeLatin2(): Boolean = all { it.latin2ByteOrNull() != null }

/**
 * Encodes to Latin-2, replacing anything the charset cannot carry with `?` - which is what an
 * encoder set to `REPLACE` does, and `REPLACE` is what `URLEncoder` uses internally.
 */
fun String.encodeLatin2(): ByteArray =
    ByteArray(length) { i -> this[i].latin2ByteOrNull() ?: QUESTION_MARK }

/**
 * `URLEncoder.encode(this, "ISO-8859-2")`, to the letter: space becomes `+`, the unreserved set
 * `[A-Za-z0-9.\-*_]` is passed through, and everything else is percent-encoded byte by byte.
 *
 * Characters Latin-2 cannot represent become `%3F` (a literal `?`), which is `URLEncoder`'s
 * behaviour too. [String.encodeHttp] is what makes sure they never get this far.
 */
fun String.percentEncodeLatin2(): String {
    val out = StringBuilder(length + ENCODE_HEADROOM)
    for (char in this) {
        when {
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in UNRESERVED -> out.append(char)
            char == ' ' -> out.append('+')
            else -> {
                val byte = (char.latin2ByteOrNull() ?: QUESTION_MARK).toInt() and 0xFF
                out.append('%').append(HEX[byte shr 4]).append(HEX[byte and 0x0F])
            }
        }
    }
    return out.toString()
}

//region code points
/**
 * `String.codePointAt` and `Character.charCount` have no common-Kotlin equivalent, and iterating a
 * string by `Char` would split a surrogate pair into two broken halves. These two are the minimum
 * needed to walk a string by code point instead.
 */
fun String.codePointAtIndex(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return CODE_POINT_OFFSET + ((high.code - MIN_HIGH_SURROGATE) shl 10) + (low.code - MIN_LOW_SURROGATE)
        }
    }
    return high.code
}

fun charCount(codePoint: Int): Int = if (codePoint >= CODE_POINT_OFFSET) 2 else 1
//endregion

private const val QUESTION_MARK: Byte = 0x3F
private const val UNRESERVED = ".-*_"
private const val HEX = "0123456789ABCDEF"
private const val ENCODE_HEADROOM = 16
private const val CODE_POINT_OFFSET = 0x10000
private const val MIN_HIGH_SURROGATE = 0xD800
private const val MIN_LOW_SURROGATE = 0xDC00
