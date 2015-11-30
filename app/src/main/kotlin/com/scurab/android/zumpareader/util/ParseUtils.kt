package com.scurab.android.zumpareader.util

import java.util.regex.Matcher
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

        /**
         * Parse single link from content
         */
        public fun parseLink(content: String): String? {
            linkPatterns.forEach {
                it.matcher(content).run {
                    if (find()) {
                        return group(1)
                    }
                }
            }
            return null
        }
    }
}
