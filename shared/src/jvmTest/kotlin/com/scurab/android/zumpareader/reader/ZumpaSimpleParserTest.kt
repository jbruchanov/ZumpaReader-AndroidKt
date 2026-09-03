package com.scurab.android.zumpareader.reader

import com.scurab.android.zumpareader.util.decodeLatin2
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The parser had no tests at all before it was moved off jsoup, `SimpleDateFormat` and
 * `android.text.Html`. These pin it against pages captured from the live forum on 2026-08-08, so
 * the port is checked against real markup rather than against a second guess about it.
 *
 * The expectations were extracted from the raw HTML independently of the parser, not recorded from
 * its output, so agreeing with them means something.
 *
 * Fixtures are stored as the bytes the server sent - ISO-8859-2, not re-encoded - which makes every
 * one of these an end-to-end check of [decodeLatin2] as well.
 *
 * Not covered: surveys. Neither captured thread contains one, and a fixture cannot be invented
 * without guessing at the markup.
 */
class ZumpaSimpleParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream(name)) { "missing fixture $name" }
            .use { it.readBytes() }
            .decodeLatin2()

    private fun Long.asLocalDateTime(): LocalDateTime =
        Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())

    private val allIds = listOf(
        "2879197", "2879193", "2879191", "2879190", "2879174", "2879173", "2879170", "2879166",
        "2879162", "2879160", "2879159", "2879150", "2879128", "2879107", "2879105", "2879094",
        "2879083", "2879079", "2879068", "2879059", "2879052", "2879036", "2879027", "2879026",
        "2879021", "2879011", "2879007", "2878999", "2878982", "2878975", "2878974", "2878968",
        "2878963", "2878953", "2878944",
    )

    //region main page
    @Test
    fun `the main page yields every row in document order`() {
        val result = ZumpaSimpleParser().parseMainPage(fixture("mainpage_default.html"))

        assertEquals(35, result.items.size)
        assertEquals(allIds, result.items.keys.toList())
    }

    @Test
    fun `the newer and older links become bare thread ids`() {
        val result = ZumpaSimpleParser().parseMainPage(fixture("mainpage_default.html"))

        //this capture has no newer page - only one nav link
        assertNull(result.prevThreadId)
        assertEquals("2878944", result.nextThreadId)
    }

    @Test
    fun `the first row is parsed field by field`() {
        val result = ZumpaSimpleParser().parseMainPage(fixture("mainpage_default.html"))
        val row = requireNotNull(result.items["2879197"])

        assertEquals("2879197", row.id)
        assertEquals("Úplná uzavírka tunelů na Pražském okruhu", row.subject)
        assertEquals("m.man", row.author)
        assertEquals(4, row.items)
        //the missing slash is not a typo - the parser concatenates the host and a relative href and
        //has always produced this. Nothing reads contentUrl, which is why it went unnoticed.
        assertEquals("https://zunpa.czread.php?f=2&i=2879197&t=2879197", row.contentUrl)
        assertEquals(LocalDateTime(2026, 8, 8, 8, 51, 34), row.time.asLocalDateTime())
        assertFalse(row.isFavorite)
        assertFalse(row.hasResponseForYou)
        assertNull(row.lastAuthor)
    }

    @Test
    fun `the last row is parsed field by field`() {
        val result = ZumpaSimpleParser().parseMainPage(fixture("mainpage_default.html"))
        val row = requireNotNull(result.items["2878944"])

        assertEquals("fancy obědárna. ..", row.subject)
        assertEquals("Black Matrix.", row.author)
        assertEquals(8, row.items)
        assertEquals(LocalDateTime(2026, 8, 6, 17, 58, 40), row.time.asLocalDateTime())
    }

    @Test
    fun `every row gets a real date and not the parse failure fallback`() {
        val result = ZumpaSimpleParser().parseMainPage(fixture("mainpage_default.html"))

        //0 is what the parser falls back to, so a strict-parsing regression shows up as a zero here
        result.items.values.forEach { row ->
            assertNotEquals(0L, row.time, "no date parsed for ${row.id} / ${row.subject}")
        }
    }

    @Test
    fun `czech diacritics survive the whole way from the wire to the model`() {
        val result = ZumpaSimpleParser().parseMainPage(fixture("mainpage_default.html"))

        assertEquals("libtardi unesli svět", result.items["2879191"]?.subject)
        assertEquals("Dobré ráno", result.items["2879193"]?.subject)
        assertTrue(result.items.values.any { it.subject.contains("ě") })
    }
    //endregion

    //region main page with the last-author column
    @Test
    fun `the last author column splits into a time and a name`() {
        val parser = ZumpaSimpleParser().apply { isShowLastUser = true }
        val result = parser.parseMainPage(fixture("mainpage_lastauthor.html"))
        val row = requireNotNull(result.items["2879197"])

        assertEquals("LSC", row.lastAuthor)
        //a time with no date lands on 1970-01-01 - see ZumpaSimpleParser.parseTimeOnly
        assertEquals(LocalDateTime(1970, 1, 1, 8, 51), row.time.asLocalDateTime())
    }

    @Test
    fun `the last author column still yields a date for every row`() {
        val parser = ZumpaSimpleParser().apply { isShowLastUser = true }
        val result = parser.parseMainPage(fixture("mainpage_lastauthor.html"))

        assertEquals(35, result.items.size)
        result.items.values.forEach { row ->
            assertNotEquals(0L, row.time, "no time parsed for ${row.id}")
            assertFalse(row.lastAuthor.isNullOrEmpty(), "no last author for ${row.id}")
        }
    }
    //endregion

    //region thread page
    @Test
    fun `a thread page yields one item per post`() {
        val result = ZumpaSimpleParser().parseThread(fixture("thread_page.html"), null)

        assertEquals(4, result.items.size)
    }

    @Test
    fun `the replying author comes off the reply2 call in the footer`() {
        val result = ZumpaSimpleParser().parseThread(fixture("thread_page.html"), null)

        assertEquals(listOf("elem", "phlanx", "peta", "Kompost"), result.items.map { it.authorReal })
    }

    @Test
    fun `every post gets its date off the Datum line`() {
        val result = ZumpaSimpleParser().parseThread(fixture("thread_page.html"), null)

        assertEquals(
            listOf(
                LocalDateTime(2026, 8, 8, 4, 39, 5),
                LocalDateTime(2026, 8, 8, 5, 27, 5),
                LocalDateTime(2026, 8, 8, 6, 51, 30),
                LocalDateTime(2026, 8, 8, 9, 6, 22),
            ),
            result.items.map { it.time.asLocalDateTime() },
        )
    }

    @Test
    fun `posts have a body and an author`() {
        val result = ZumpaSimpleParser().parseThread(fixture("thread_page.html"), null)

        result.items.forEachIndexed { index, item ->
            assertFalse(item.author.isEmpty(), "no author on post $index")
            assertFalse(item.body.isEmpty(), "no body on post $index")
        }
    }

    /**
     * The `<br>` closing the date line is a separator too, so `lines[3]` is empty on every post and
     * the body used to come out starting with a blank line. Nothing downstream trimmed it, so every
     * message on the thread screen was drawn one empty line below its author - which reads as the
     * row being badly padded rather than as a stray line.
     */
    @Test
    fun `a body does not start or end with a blank line`() {
        val result = ZumpaSimpleParser().parseThread(fixture("thread_page.html"), null)

        result.items.forEachIndexed { index, item ->
            assertFalse(item.body.startsWith("\n"), "leading blank line on post $index")
            assertFalse(item.body.endsWith("\n"), "trailing blank line on post $index")
        }
    }

    /** The newlines between the lines of a message are the message. Only the ends are trimmed. */
    @Test
    fun `the line breaks inside a message are kept`() {
        val result = ZumpaSimpleParser().parseThread(fixture("thread_page.html"), null)

        val multiLine = result.items.first { it.body.contains("\n") }
        assertTrue(multiLine.body.lines().size > 1)
        assertFalse(multiLine.body.lines().first().isEmpty())
    }

    /**
     * The forum's message bodies close each line with `<br />`, sat on its own source line. The
     * body used to split on the literal `<br>` only - so `<br />` slipped through, `htmlToText`
     * turned the tag into a `\n` AND kept the source `\n` after it, and every line got a blank one
     * after it (Ksoup's `wholeText` renders `<br>` as a newline; jsoup's did not). One
     * source `<br />\n` should give one output `\n`, not two.
     */
    @Test
    fun `two adjacent lines are not separated by a blank one`() {
        val result = ZumpaSimpleParser().parseThread(fixture("thread_page.html"), null)

        //peta's post is 'elem » <br />\nbrrrre' - one <br />, no blank line intended
        val post = result.items.first { it.body.contains("brrrre") }
        assertEquals(listOf("elem » ", "brrrre"), post.body.lines())
    }

    @Test
    fun `own posts are flagged when the user name matches`() {
        val result = ZumpaSimpleParser().parseThread(fixture("thread_page.html"), "peta")

        assertEquals(listOf(false, false, true, false), result.items.map { it.isOwnThread })
    }

    @Test
    fun `a long thread walks every post and not just the first few`() {
        //146 tables -> limit 141 -> the index advances in threes -> 47 posts
        val result = ZumpaSimpleParser().parseThread(fixture("thread_survey.html"), null)

        assertEquals(47, result.items.size)
        result.items.forEachIndexed { index, item ->
            assertNotEquals(0L, item.time, "no date on post $index")
        }
    }

    @Test
    fun `the first and last post of the long thread name their author`() {
        val result = ZumpaSimpleParser().parseThread(fixture("thread_survey.html"), null)

        assertEquals("Blah", result.items.first().authorReal)
        assertFalse(result.items.last().authorReal.isNullOrEmpty())
    }
    //endregion

    //region static helpers
    @Test
    fun `a thread id is pulled out of a forum link`() {
        assertEquals(
            2879197,
            ZumpaSimpleParser.getZumpaThreadId("https://zunpa.cz/phorum/read.php?f=2&i=1&t=2879197"),
        )
        assertEquals(0, ZumpaSimpleParser.getZumpaThreadId("https://example.com"))
        assertEquals(0, ZumpaSimpleParser.getZumpaThreadId(null))
    }

    @Test
    fun `the uid is pulled out of a profile link`() {
        assertEquals("abc123", ZumpaSimpleParser.parseUID("<a href='profile.php?uid=abc123'>x</a>"))
        assertNull(ZumpaSimpleParser.parseUID("nothing here"))
        assertNull(ZumpaSimpleParser.parseUID(null))
    }

    @Test
    fun `urls are collected and html escapes in them decoded`() {
        val links = ZumpaSimpleParser.getLinks("see http://a.b/x?y=1&amp;z=2 and https://c.d/e")

        assertEquals(setOf("http://a.b/x?y=1&z=2", "https://c.d/e"), links)
    }

    /**
     * Two urls on their own lines are two buttons - the collector picks both up. Regression: a bare
     * `www.` link used to be dropped by the http-only pattern, so a message with `www.foo` and
     * `http://bar` only ever showed one button.
     */
    @Test
    fun `both http and www urls are collected`() {
        val links = ZumpaSimpleParser.getLinks("www.prdel.cz\nhttp://hovno.cz")

        assertEquals(setOf("www.prdel.cz", "http://hovno.cz"), links)
    }

    /**
     * A url written inside `<url>` used to trail a `>` on its label - `[^<"\s]*` did not stop on
     * `>`, so the closing bracket was swallowed and the button opened `www.a.b>`, which is not a
     * url. Excluding `>` from the body class fixes it - a legitimate `>` in a url must be
     * `%3E` anyway.
     */
    @Test
    fun `an angle-wrapped url does not keep its closing bracket`() {
        val links = ZumpaSimpleParser.getLinks("<www.a.b> and <http://c.d>")

        assertEquals(setOf("www.a.b", "http://c.d"), links)
    }

    /**
     * The wire form of `<url>` is `&lt;url&gt;`. Entities are decoded before the regex runs, not
     * after - `&gt;` is four ordinary characters to `[^<>"\s]*` and used to be swept up as part of
     * the url, leaving the button label reading `www.a.b>`.
     */
    @Test
    fun `an entity-wrapped url is unescaped before the regex sees it`() {
        assertEquals(setOf("www.a.b"), ZumpaSimpleParser.getLinks("&lt;www.a.b&gt;"))
        assertEquals(setOf("http://c.d"), ZumpaSimpleParser.getLinks("&lt;http://c.d&gt;"))
    }

    @Test
    fun `links are wrapped in angle brackets`() {
        assertEquals(
            "go to <https://a.b/c> now",
            ZumpaSimpleParser.replaceLinksByZumpaLinks("go to https://a.b/c now"),
        )
        assertNull(ZumpaSimpleParser.replaceLinksByZumpaLinks(null))
    }

    /**
     * A bare `www.` counts too - the forum's link parser accepts one AND needs a scheme to make it
     * clickable, so the wrapper adds `https://` on the way in. `http://a.b/c` already has one, so
     * it is only bracketed.
     */
    @Test
    fun `a bare www link is wrapped and given an https scheme`() {
        assertEquals(
            "see <https://www.test.com> and <https://a.b/c>",
            ZumpaSimpleParser.replaceLinksByZumpaLinks("see www.test.com and https://a.b/c"),
        )
    }

    /** Once wrapped, a url does not gain a second pair - `<<http://a.b>>` is not a link. */
    @Test
    fun `an already wrapped link is left alone`() {
        assertEquals(
            "keep <http://a.b/c> as is",
            ZumpaSimpleParser.replaceLinksByZumpaLinks("keep <http://a.b/c> as is"),
        )
    }

    /** Sentence punctuation the writer meant as punctuation, not as the tail of a url. */
    @Test
    fun `trailing punctuation stays outside the brackets`() {
        assertEquals(
            "look at <https://a.b/c>, then <https://www.d.e>.",
            ZumpaSimpleParser.replaceLinksByZumpaLinks("look at https://a.b/c, then www.d.e."),
        )
    }

    /** A `www` that is the tail of an unrelated word is not a link. */
    @Test
    fun `www embedded in a word is not wrapped`() {
        assertEquals(
            "notwww.test.com stays put",
            ZumpaSimpleParser.replaceLinksByZumpaLinks("notwww.test.com stays put"),
        )
    }

    @Test
    fun `a push message is split into its three fields`() {
        val message = ZumpaSimpleParser.parsePushMessage("ID=123;F=someone;MSG=hello")

        assertEquals("123", message.threadId)
        assertEquals("someone", message.from)
        assertEquals("hello", message.message)
    }

    @Test
    fun `the survey response count is read out of its label`() {
        assertEquals(42, ZumpaSimpleParser.getSurveyResponsesFromText("otazka (42 odp.)"))
        assertEquals(-1, ZumpaSimpleParser.getSurveyResponsesFromText("no count here"))
    }
    //endregion
}
