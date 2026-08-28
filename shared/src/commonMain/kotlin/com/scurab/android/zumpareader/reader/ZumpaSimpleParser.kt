package com.scurab.android.zumpareader.reader

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.model.Survey
import com.scurab.android.zumpareader.model.SurveyItem
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaPushMessage
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.HtmlTags.ATTR_HREF
import com.scurab.android.zumpareader.reader.HtmlTags.ATTR_ID
import com.scurab.android.zumpareader.reader.HtmlTags.ATTR_REL
import com.scurab.android.zumpareader.reader.HtmlTags.ATTR_TITLE
import com.scurab.android.zumpareader.reader.HtmlTags.CLASS
import com.scurab.android.zumpareader.reader.HtmlTags.IS_FAVOURITE_CLASS
import com.scurab.android.zumpareader.reader.HtmlTags.NBSP
import com.scurab.android.zumpareader.reader.HtmlTags.NBSP_CHAR
import com.scurab.android.zumpareader.reader.HtmlTags.NBSP_CHAR_STR
import com.scurab.android.zumpareader.reader.HtmlTags.TAG_BOLD
import com.scurab.android.zumpareader.reader.HtmlTags.TAG_DIV
import com.scurab.android.zumpareader.reader.HtmlTags.TAG_HREF
import com.scurab.android.zumpareader.reader.HtmlTags.TAG_IMG
import com.scurab.android.zumpareader.reader.HtmlTags.TAG_LI
import com.scurab.android.zumpareader.reader.HtmlTags.TAG_SPAN
import com.scurab.android.zumpareader.reader.HtmlTags.TAG_TABLE
import com.scurab.android.zumpareader.reader.HtmlTags.TAG_TABLE_COLUMS
import com.scurab.android.zumpareader.reader.HtmlTags.TAG_TABLE_ROW
import com.scurab.android.zumpareader.util.htmlToText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant

/**
 * Scrapes the forum's HTML into the model. Ported from Java, jsoup and `SimpleDateFormat` to
 * Kotlin, Ksoup and `kotlinx-datetime` - see `KMP_PLAN.md`. The scraping itself is unchanged,
 * including the parts that look odd; `ZumpaSimpleParserTest` pins the output against captured pages.
 *
 * The `InputStream` entry points are gone: decoding is [com.scurab.android.zumpareader.util.decodeLatin2]'s
 * job now, and it hands over a `String`.
 */
class ZumpaSimpleParser {

    var isShowLastUser: Boolean = false
    var userName: String? = null

    //region MainPage
    fun parseMainPage(html: String): ZumpaMainPageResult = parseMainPage(Ksoup.parse(html))

    fun parseMainPage(doc: Document): ZumpaMainPageResult {
        val elems = doc.getElementsByTag(TAG_TABLE)
        val li = elems.listIterator()
        val topTable = li.next()
        val mainTable = li.next()
        li.next() // bottomTable, unused

        val links = handleParsingTopTable(topTable)
        val items = parseContent(mainTable)

        return ZumpaMainPageResult(links.first, links.second!!, items)
    }

    private fun handleParsingTopTable(elem: Element): Pair<String?, String?> {
        val cols = elem.getElementsByTag(TAG_TABLE_COLUMS)
        val els = cols[cols.size - 1].getElementsByTag(TAG_HREF)

        var next: String? = null
        var prev: String? = null
        when (els.size) {
            1 -> next = els[0].attr(ATTR_HREF) // no prev page
            2 -> {
                prev = els[0].attr(ATTR_HREF)
                next = els[1].attr(ATTR_HREF)
            }
        }
        return extractQueryString(prev, "t") to extractQueryString(next, "t")
    }

    private fun extractQueryString(value: String?, key: String): String? {
        if (value == null) {
            return null
        }
        val needle = "$key="
        val start = value.indexOf(needle) + needle.length
        if (start < 0) {
            return value
        }
        var end = value.indexOf("&", start)
        if (end < 0) {
            end = value.length
        }
        return value.substring(start, end)
    }

    private fun parseContent(elem: Element): LinkedHashMap<String, ZumpaThread> {
        val result = LinkedHashMap<String, ZumpaThread>()

        val elems = elem.getElementsByTag(TAG_TABLE_ROW)
        val li = elems.listIterator()
        li.next() // first line is header
        while (li.hasNext()) {
            val el = li.next()
            val columns = el.getElementsByTag(TAG_TABLE_COLUMS)
            val zumpaItem = parseThreadRow(columns)
            result[zumpaItem.id] = zumpaItem
        }
        return result
    }

    /** One row of the list table. Named apart from [parseThread] so the overloads cannot collide. */
    private fun parseThreadRow(columns: List<Element>): ZumpaThread {
        val li = columns.listIterator()
        val first = li.next()

        val subElem = first.getElementsByTag(TAG_HREF).first()!!
        val url = subElem.attr(ATTR_HREF)
        val id = subElem.attr(ATTR_REL)
        val subject = subElem.text()

        val second = li.next()
        val authorHtml = second.html().replace(NBSP, "").trim()

        val third = li.next()
        val answers = third.text()

        li.next() // fourth - read
        li.next() // fifth - complete

        val sixth = li.next() // complete
        var time: String
        var lastAnswerAuthor: String? = null

        if (isShowLastUser) {
            try {
                val sub = sixth.text()
                val vals = sub.split(" ")
                time = vals[0]
                lastAnswerAuthor = vals[vals.size - 1] // can be date between
            } catch (t: Throwable) {
                t.printStackTrace()
                time = sixth.text()
            }
        } else {
            time = sixth.text()
        }
        time = time.replace(NBSP_CHAR, ' ')

        val isFavourite = getIsFavourite(first)
        val hasResponseForYou = getHasResponseForYou(first)
        val responses = safeInt(answers, 0)

        val author = authorHtml.htmlToText()
        val contentUrl = ZR.Constants.ZUMPA_MAIN_URL + url
        val timeValue = if (isShowLastUser) parseTimeOnly(time) else parseFullDate(time)

        return ZumpaThread(id, subject).apply {
            this.author = author
            this.lastAuthor = lastAnswerAuthor
            this.contentUrl = contentUrl
            this.time = timeValue
            this.items = responses
            this.isFavorite = isFavourite
            this.hasResponseForYou = hasResponseForYou
        }
    }

    private fun getIsFavourite(first: Element): Boolean =
        first.hasAttr(CLASS) && first.attr(CLASS) == IS_FAVOURITE_CLASS

    private fun getHasResponseForYou(elem: Element): Boolean {
        val subImgs = elem.getElementsByTag(TAG_IMG)
        if (subImgs.isEmpty()) {
            return false
        }
        val e = subImgs.first()!!
        return e.hasAttr(ATTR_TITLE) && e.attr(ATTR_TITLE).contains("*")
    }
    //endregion MainPage

    //region thread
    fun parseThread(html: String, userName: String?): ZumpaThreadResult =
        parseThread(Ksoup.parse(html), userName)

    fun parseThread(doc: Document, userName: String?): ZumpaThreadResult {
        val result = ArrayList<ZumpaThreadItem>()

        val elems = doc.getElementsByTag(TAG_TABLE)
        val size = elems.size
        if (size > 0) { // should be always at min 1
            val li = elems.listIterator()
            li.next() // header

            val limit = size - 5
            //the index walks in threes: the original was a for loop with two extra i++ in its body
            var i = 0
            while (i < limit) {
                i++

                val parent = li.next()
                //the inside row is where the final table is
                var elem = parent.getElementsByTag(TAG_TABLE_ROW)[1]
                elem = elem.getElementsByTag(TAG_TABLE).first()!!
                val zsi = parseThreadItem(elem, userName)!!
                result.add(zsi)

                li.next() // footer
                val footer = li.next() // footer

                zsi.authorReal = getAuthorNameFromResponse(footer)
                zsi.isOwnThread = userName != null && userName == zsi.authorReal

                if (i == 1) { // only the first element can contain a survey
                    zsi.survey = parseSurvey(elem)
                }
                i++
                i++
            }
        }
        return ZumpaThreadResult(result)
    }

    private fun parseThreadItem(element: Element, userName: String?): ZumpaThreadItem? {
        val elems = element.getElementsByTag(TAG_TABLE_COLUMS)
        if (elems.size != 1) { // should be always 1
            return null
        }
        val elem = elems.first()!!
        val content = elem.html()
        val author = getAuthorName(elem)
        var rating = elem.child(2).text()
        if (rating.isEmpty()) {
            rating = elem.child(1).text()
        }
        val date = getTime(content)

        val sb = StringBuilder()
        var urls: MutableSet<String>? = null

        val lines = content.split(BR_SEPARATOR)
        for (index in 3 until lines.size) {
            var line = lines[index]
            urls = getLinks(line, urls)
            line = line.htmlToText()
            sb.append(line).append("\n")
        }

        /*
         * Trimmed of the newlines at either end, not just the one the loop leaves at the end.
         *
         * The `<br>` that closes the date line is itself a separator, so `lines[3]` is empty on
         * every post and the body came out starting with a blank line. Nothing downstream trimmed
         * it, so every message on the thread screen was drawn one empty line below its author -
         * which reads as the row having too much padding rather than as a stray line.
         *
         * Only the newlines: the leading spaces of an indented line are the writer's.
         */
        val body = sb.toString().trim('\n')

        return ZumpaThreadItem(author, body, date).apply {
            this.rating = rating
            if (urls != null) {
                this.urls = ArrayList(urls)
            }
            if (!userName.isNullOrEmpty()) {
                this.hasResponseForYou = body.contains(createToMeTemplate(userName))
            }
        }
    }

    private fun createToMeTemplate(userName: String): String = "$userName$NBSP_CHAR»"

    private fun getAuthorNameFromResponse(footer: Element): String =
        AUTHOR_FROM_RESPONSE_PATTERN.find(footer.html())
            ?.groupValues?.get(1)
            ?.htmlToText()
            ?: ""

    private fun getAuthorName(elem: Element): String {
        var value = elem.textNodes()[0].getWholeText()
        val start = value.indexOf(NBSP_CHAR) + 1
        val end = value.length
        value = if (start >= end) {
            elem.child(1).text()
        } else {
            value.substring(start, end).trim().let {
                if (it.contains("(") && !it.contains(")")) "$it)" else it
            }
        }
        return value.replace(NBSP_CHAR_STR, " ")
    }

    private fun getTime(data: String): Long =
        parseFullDate(getGroup(DATE_PATTERN, data, 1, ""))
    //endregion thread

    //region survey
    private fun parseSurvey(subDoc: Element): Survey? {
        for (e in subDoc.getElementsByTag(TAG_DIV)) {
            val id = e.attr(ATTR_ID)
            if (SURVEY_ID_PATTERN.matches(id)) {
                return parseSurveyImpl(e, id.replace("ank", ""))
            }
        }
        return null
    }

    private fun parseSurveyImpl(e: Element, id: String): Survey =
        Survey(id, parseQuestion(e), parseResponses(e), parseSurveyItems(e, id))

    private fun parseQuestion(e: Element): String = e.getElementsByTag(TAG_SPAN).first()!!.text()

    private fun parseResponses(e: Element): Int =
        getSurveyResponsesFromText(e.text().replace(NBSP_CHAR, ' '))

    private fun parseSurveyItems(e: Element, surveyId: String): List<SurveyItem> {
        val result = ArrayList<SurveyItem>()
        var order = 1
        for (li in e.getElementsByTag(TAG_LI)) {
            result.add(parseSurveyRow(order, surveyId, li))
            order++
        }
        return result
    }

    private fun parseSurveyRow(order: Int, surveyId: String, li: Element): SurveyItem {
        var a = li.getElementsByTag(TAG_HREF).first()
        val voted = a == null
        if (a == null) {
            a = li.getElementsByTag(TAG_BOLD).first()
        }
        val innerDiv = li.getElementsByTag(TAG_DIV).first()!!
        val percts = innerDiv.html().replace(NBSP, "").replace("%", "").trim()
        return SurveyItem(order, surveyId, a!!.text(), safeInt(percts, 0), voted)
    }
    //endregion survey

    companion object {
        val RESPONSE_PATTERN = Regex("(.+)\\s?»", RegexOption.IGNORE_CASE)
        val URL_PATTERN2 = Regex(">?(http[s]?://[^<\"\\s]*)<?", RegexOption.IGNORE_CASE)
        private val DATE_PATTERN = Regex("Datum:&nbsp;([^<]+)", RegexOption.IGNORE_CASE)
        private val SURVEY_RESPONSE_PATTERN = Regex("\\((\\d*) odp.\\)", RegexOption.IGNORE_CASE)
        private val ZUMPA_LINK = Regex("zunpa.cz/phorum/read.php.*t=(\\d+)", RegexOption.IGNORE_CASE)
        private val USER_ID_PATTERN = Regex("profile.php\\?uid=([a-z0-9]*)'", RegexOption.IGNORE_CASE)
        private val AUTHOR_FROM_RESPONSE_PATTERN = Regex("reply2\\('@(.*):", RegexOption.MULTILINE)
        private val SURVEY_ID_PATTERN = Regex("ank\\d*")

        /** The body is split on this before the tags are stripped, so `<br>` never reaches Ksoup. */
        private const val BR_SEPARATOR = "<br>"

        const val ZUMPA_PUSH_KEY_NOTIFICAION = "ZUMPA"
        const val ZUMPA_UPDATE_KEY_NOTIFICAION = "UPDATE"
        const val VALUE_SEPARATOR = "="
        const val ITEM_SEPARATOR = ";"
        const val ZUMPA_PUSH_KEY_THREAD_ID = "ID"
        const val ZUMPA_PUSH_KEY_FROM = "F"
        const val ZUMPA_PUSH_KEY_MESSAGE = "MSG"
        const val ZUMPA_PUSH_KEY_HIDENOTIFICATION = "ZUMPA_PUSH_KEY_HIDENOTIFICATION"
        const val ZUMPA_PUSH_KEY_HIDENOTIFICATION_ID = 974561

        //region dates
        /** `SimpleDateFormat("dd. MM. yyyy HH:mm:ss")`. */
        private val FULL_DATE_FORMAT = LocalDateTime.Format {
            day(); chars(". "); monthNumber(); chars(". "); year()
            char(' ')
            hour(); char(':'); minute(); char(':'); second()
        }

        /** `SimpleDateFormat("HH:mm")`. */
        private val TIME_FORMAT = LocalTime.Format {
            hour(); char(':'); minute()
        }

        private val EPOCH_DATE = LocalDate(1970, 1, 1)

        /**
         * The value is trimmed first. `SimpleDateFormat` stopped at the end of the pattern and
         * ignored whatever followed; `kotlinx-datetime` insists the whole string is consumed. The
         * dates lifted out of a post come from Ksoup's pretty-printed `html()`, which leaves a
         * newline behind, so without the trim every thread post would fall back to 0.
         */
        private fun parseFullDate(value: String): Long = runCatching {
            LocalDateTime.parse(value.trim(), FULL_DATE_FORMAT).toEpochMillis()
        }.getOrElse { 0L }

        /**
         * A time with no date, which is what the list sends when the last-author column is on.
         * `SimpleDateFormat("HH:mm").parse("08:51")` yields 1970-01-01 at that local time, and the
         * list relies on the resulting ordering, so that is reproduced rather than corrected.
         */
        private fun parseTimeOnly(value: String): Long = runCatching {
            EPOCH_DATE.atTime(LocalTime.parse(value.trim(), TIME_FORMAT)).toEpochMillis()
        }.getOrElse { 0L }

        private fun LocalDateTime.toEpochMillis(): Long =
            toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        //endregion

        private fun safeInt(text: String, defValue: Int): Int =
            text.replace(NBSP_CHAR_STR, "").trim().toIntOrNull() ?: defValue

        private fun getGroup(pattern: Regex, value: String, group: Int, defValue: String): String =
            pattern.find(value)?.groupValues?.get(group) ?: defValue

        fun getSurveyResponsesFromText(text: String): Int =
            SURVEY_RESPONSE_PATTERN.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -1

        fun getZumpaThreadId(link: String?): Int {
            if (link == null) {
                return 0
            }
            return ZUMPA_LINK.find(link)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

        fun parseUID(content: String?): String? =
            content?.let { USER_ID_PATTERN.find(it)?.groupValues?.get(1) }

        fun replaceLinksByZumpaLinks(text: String?): String? {
            var result = text ?: return null
            for (link in getLinks(result)) {
                result = result.replace(link, "<$link>")
            }
            return result
        }

        fun getLinks(text: String): Set<String> = getLinks(text, HashSet())!!

        private fun getLinks(text: String, toFill: MutableSet<String>?): MutableSet<String>? {
            var links = toFill
            for (match in URL_PATTERN2.findAll(text)) {
                val link = match.groupValues[1]
                if (link.isNotEmpty()) {
                    if (links == null) {
                        links = HashSet()
                    }
                    links.add(link.htmlToText()) // decode html escapes
                }
            }
            return links
        }

        fun parsePushMessage(data: String): ZumpaPushMessage {
            var threadId: String? = null
            var from: String? = null
            var message: String? = null
            for (item in data.split(ITEM_SEPARATOR)) {
                val itemValues = item.split(VALUE_SEPARATOR)
                val key = itemValues[0]
                val value = itemValues[1]
                when (key) {
                    ZUMPA_PUSH_KEY_THREAD_ID -> threadId = value
                    ZUMPA_PUSH_KEY_FROM -> from = value
                    ZUMPA_PUSH_KEY_MESSAGE -> message = value
                }
            }
            return ZumpaPushMessage(threadId!!, from!!, message)
        }
    }
}
