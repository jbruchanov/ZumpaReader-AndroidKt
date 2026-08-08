package com.scurab.android.zumpareader.reader

internal object HtmlTags {
    const val TAG_TABLE = "table"
    const val TAG_TABLE_ROW = "tr"
    const val TAG_TABLE_COLUMS = "td"
    const val TAG_HREF = "a"
    const val TAG_IMG = "img"
    const val TAG_SPAN = "span"
    const val TAG_LI = "LI"
    const val ATTR_HREF = "href"
    const val ATTR_REL = "rel"
    const val ATTR_ID = "id"
    const val CLASS = "class"
    const val IS_FAVOURITE_CLASS = "qtds-hi"
    const val ATTR_TITLE = "title"
    const val TAG_DIV = "div"
    const val NBSP = "&nbsp;"
    const val TAG_BOLD = "b"
    val NBSP_CHAR: Char = 160.toChar()
    val NBSP_CHAR_STR: String = NBSP_CHAR.toString()
    const val TAG_BOLD_START = "<b>"
    const val TAG_BOLD_END = "</b>"
}
