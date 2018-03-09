package com.scurab.android.zumpareader.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.text.Html
import com.scurab.android.zumpareader.model.ZumpaGenericResponse
import okhttp3.Headers
import retrofit2.Response
import java.security.MessageDigest
import java.util.*
import java.util.regex.Pattern

/**
 * Created by JBruchanov on 25/11/2015.
 */

class ParseUtils {
    companion object {
        private val linkPatterns: Array<Pattern> = arrayOf(
                Pattern.compile("<a[^>]*href=\"([^\"]*)[^>]*>(.*)</a>", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(http[s]?://[^\\s]*)", Pattern.CASE_INSENSITIVE),
                android.util.Patterns.WEB_URL)

        private val phpSessionPattern = Pattern.compile("PHPSESSID=(\\w+);")

        /**
         * Parse single link from content
         */
        fun parseLink(content: String): String? {
            linkPatterns.forEach {
                it.matcher(content).run {
                    if (find()) {
                        return Html.fromHtml(group(1)).toString()
                    }
                }
            }
            return null
        }

        fun hasPHPSessionId(value: String?): Boolean {
            return value?.contains("PHPSESSID") ?: false
        }

        fun extractPHPSessionId(headers: Headers?): String? {
            headers?.let {
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

        fun extractPHPSessionId(value: String?): String? {
            value.let {
                phpSessionPattern.matcher(value).let {
                    if (it.find()) {
                        return it.group(1)
                    }
                }
            }
            return null
        }

        fun resizeImageIfNecessary(byteArray: ByteArray, displaySize: Point): Bitmap? {
            var opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, opts)
            var imWidth = opts.outWidth
            var imMax = Math.max(opts.outWidth, opts.outHeight)
            var dispWidth = Math.min(displaySize.x, displaySize.y)
            var resize = 1
            while (imWidth > 0 && imWidth > 1.5f * dispWidth || imMax > 4096/*oGL limit*/) {
                resize *= 2
                imWidth /= 2
                imMax /= 2
            }
            opts.inJustDecodeBounds = false
            opts.inSampleSize = resize
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, opts)
        }

        fun MD5(value: String): String? {
            try {
                val md = MessageDigest.getInstance("MD5")
                val array = md.digest(value.toByteArray())
                val sb = StringBuffer()
                for (i in array) {
                    sb.append(Integer.toHexString((i.toInt() and 0xFF) or 0x100).substring(1, 3))
                }
                return sb.toString()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return null
        }

        fun extractCookies(it: Response<ZumpaGenericResponse?>): Set<String> {
            var cookies = it.headers().toMultimap()["Set-Cookie"] as List<String>
            return HashSet<String>(cookies)
        }

        fun extractSessionId(it: Response<ZumpaGenericResponse?>): String? {
            var cookies = it.headers().toMultimap()["Set-Cookie"] as List<String>
            var sessionId: String? = null
            for (c in cookies) {
                if (c.contains("PHPSESSID")) {
                    val matcher = Pattern.compile("PHPSESSID=([^;]+);").matcher(c)
                    if (matcher.find()) {
                        sessionId = matcher.group(1)
                        break
                    }
                }
            }
            return sessionId
        }
    }
}
