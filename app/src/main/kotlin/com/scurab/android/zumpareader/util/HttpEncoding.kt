package com.scurab.android.zumpareader.util

import com.scurab.android.zumpareader.ZR
import java.net.URLEncoder
import java.nio.charset.Charset

/** The forum is a legacy ISO-8859-2 site, so that is the charset every form body is encoded in. */
private val zumpaCharset: Charset = Charset.forName(ZR.Constants.ENCODING)

/**
 * Form-encodes a value the way a browser would on an ISO-8859-2 page.
 *
 * `URLEncoder` turns anything the charset cannot represent into a literal `?` - which is why emoji,
 * and everything else outside Latin-2, used to arrive at the forum as question marks or not at all.
 * A browser does not do that: when a form's charset cannot encode a character it substitutes an
 * **HTML numeric character reference** (`&#128512;`), which the forum stores and renders back as the
 * character. So do we.
 *
 * A `&` the user typed is left alone and percent-encoded as itself, exactly as a browser leaves it;
 * only characters the charset genuinely cannot carry are replaced.
 */
fun String.encodeHttp(): String = URLEncoder.encode(escapeUnencodable(), zumpaCharset.name())

/**
 * Replaces every code point [zumpaCharset] cannot represent with `&#<decimal>;`. Iterates by code
 * point, not by char, so an emoji's surrogate pair becomes one reference rather than two broken
 * halves.
 */
private fun String.escapeUnencodable(): String {
    val encoder = zumpaCharset.newEncoder()
    if (encoder.canEncode(this)) {
        return this
    }
    val out = StringBuilder(length + ESCAPE_HEADROOM)
    var i = 0
    while (i < length) {
        val codePoint = codePointAt(i)
        val chars = Character.charCount(codePoint)
        val chunk = substring(i, i + chars)
        if (encoder.canEncode(chunk)) {
            out.append(chunk)
        } else {
            out.append("&#").append(codePoint).append(';')
        }
        i += chars
    }
    return out.toString()
}

private const val ESCAPE_HEADROOM = 16
