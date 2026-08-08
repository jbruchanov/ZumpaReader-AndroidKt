package com.scurab.android.zumpareader

object ZR {
    object Constants {
        const val ZUMPA_MAIN_URL = "https://zunpa.cz"
        const val ZUMPA_WS_MAIN_URL = "http://zumpaws.scurab.com:8104"
        const val ZUMPA_PHP_MAIN_URL = "http://zumpareader.scurab.com"

        /**
         * The forum is a legacy ISO-8859-2 site. Decoding is done by
         * [com.scurab.android.zumpareader.util.decodeLatin2], this is the name for the wire.
         */
        const val ENCODING = "ISO-8859-2"
        const val ZUMPA_SHOW_LAST_ANSWER_AUTHOR_KEY = "newdate"
        const val ZUMPA_THREAD_LINK = "$ZUMPA_MAIN_URL/phorum/read.php?f=2&i=%1\$s&t=%1\$s"

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; U; Android 2.2; en-us; Nexus One Build/FRF91) " +
                "AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1"
    }
}
