package com.scurab.android.zumpareader.util

import java.security.MessageDigest

/**
 * Created by JBruchanov on 25/11/2015.
 *
 * What is left of this after the move to Ktor. `parseLink`, `hasPHPSessionId`,
 * `extractPHPSessionId`, `extractSessionId` and `resizeImageIfNecessary` were all dead - some of
 * them since the okhttp cookie jar took over the session handling - and with them went the last
 * uses of `android.text.Html`, `android.util.Patterns`, `Bitmap` and okhttp's `Headers` in this
 * file. `extractCookies` is gone too: the login response carries its own `Set-Cookie`s now, see
 * [com.scurab.android.zumpareader.model.ZumpaGenericResponse.setCookies].
 */
class ParseUtils {
    companion object {

        /** Still JVM-only - `MessageDigest` needs replacing before this can move to common code. */
        fun MD5(value: String): String? {
            try {
                val md = MessageDigest.getInstance("MD5")
                val array = md.digest(value.toByteArray())
                val sb = StringBuilder()
                for (i in array) {
                    sb.append(Integer.toHexString((i.toInt() and 0xFF) or 0x100).substring(1, 3))
                }
                return sb.toString()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return null
        }
    }
}
