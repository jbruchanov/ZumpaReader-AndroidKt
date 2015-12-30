package com.scurab.android.zumpareader.util

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.text.Html
import com.pawegio.kandroid.i
import com.squareup.okhttp.Headers
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Created by JBruchanov on 25/11/2015.
 */

public class ParseUtils {
    companion object {
        private val linkPatterns: Array<Pattern> = arrayOf(
                Pattern.compile("<a[^>]*href=\"([^\"]*)[^>]*>(.*)</a>", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(http[s]?://[^\\s]*)", Pattern.CASE_INSENSITIVE),
                android.util.Patterns.WEB_URL)

        private val phpSessionPattern = Pattern.compile("PHPSESSID=(\\w+);")

        /**
         * Parse single link from content
         */
        public fun parseLink(content: String): String? {
            linkPatterns.forEach {
                it.matcher(content).run {
                    if (find()) {
                        return Html.fromHtml(group(1)).toString()
                    }
                }
            }
            return null
        }

        public fun hasPHPSessionId(value: String?): Boolean {
            return value?.contains("PHPSESSID") ?: false
        }

        public fun extractPHPSessionId(headers: Headers?) : String? {
            headers.exec {
                for (i in 0..it.size()) {
                    if ("Set-Cookie".equals(it.name(i), true)) {
                        val value = it.value(i)
                        if (ParseUtils.hasPHPSessionId(value)) {
                            val sessionId = Companion.extractPHPSessionId(value)
                            if (sessionId != null) {
                                return sessionId
                            }
                        }
                    }
                }
            }
            return null
        }

        public fun extractPHPSessionId(value: String?): String? {
            value.exec {
                phpSessionPattern.matcher(value).exec {
                    if (it.find()) {
                        return it.group(1)
                    }
                }
            }
            return null
        }

        public fun resizeImageIfNecessary(byteArray: ByteArray, displaySize: Point): Bitmap? {
            var opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, opts)
            var imWidth = opts.outWidth
            var dispWidth = Math.min(displaySize.x, displaySize.y)
            var resize = 1
            while (imWidth > 0 && imWidth > 1.5f * dispWidth) {
                resize *= 2
                imWidth /= 2
            }
            opts.inJustDecodeBounds = false
            opts.inSampleSize = resize
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, opts)
        }

        public fun MD5(value: String): String? {
            try {
                val md = MessageDigest.getInstance("MD5");
                val array = md.digest(value.toByteArray());
                val sb = StringBuffer();
                for (i in array) {
                    sb.append(Integer.toHexString((i.toInt() and 0xFF) or 0x100).substring(1, 3));
                }
                return sb.toString();
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return null;
        }
    }
}
