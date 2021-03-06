package com.scurab.android.zumpareader.model

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.nullArray
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.gson.GsonExclude
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.encodeHttp
import java.nio.charset.Charset
import java.util.*

/**
 * Created by JBruchanov on 24/11/2015.
 */

data class ZumpaThread
constructor(val id: String,
                   var subject: String) {

    companion object {
        val STATE_NONE = 0
        val STATE_NEW = 1
        val STATE_UPDATED = 2
        val STATE_OWN = 3
        val STATE_RESPONSE_4U = 4

        fun thread(elem: JsonObject): ZumpaThread {
            return ZumpaThread(elem["id"].string, elem["subject"].string).apply {
                this.author = elem["author"].string
                this.time = elem["time"].long
                this.lastAuthor = elem.get("lastAuthor").nullString
                this.offlineItems = elem["offlineItems"].asJsonArray.asItems()
                this.items = Math.max(0, (this.offlineItems?.count() ?: 0) - 1)
                this.state = STATE_NONE
            }
        }

        fun JsonArray.asItems(): List<ZumpaThreadItem> {
            return map { v -> (v as JsonObject).asItem() }
        }

        fun JsonObject.asItem(): ZumpaThreadItem {
            return ZumpaThreadItem(this["author"].string, this["body"].string, this["time"].long).apply {
                this.authorReal = this@asItem["authorReal"].string
                this.urls = this@asItem.get("urls").nullArray?.map { v -> v.string }
            }
        }
    }

    constructor(id: String,
                       subject: String,
                       author: String,
                       contentUrl: String,
                       time: Long) : this(id, subject) {
        this.author = author
        this.contentUrl = contentUrl
        this.time = time
    }

    var author: String = ""
    var contentUrl: String = ""
    var time: Long = 0L
    private var _items = 0
    var items: Int
        get() = _items
        set(value) {
            _items = value
        }

    fun setStateBasedOnReadValue(readCount: Int?, userName: String?) {
        if (hasResponseForYou) {
            state = STATE_RESPONSE_4U
        } else if (readCount == null) {
            state = STATE_NEW
        } else if (items == readCount) {
            if (userName != null && userName == author) {
                state = STATE_OWN
            } else {
                state = STATE_NONE
            }
        } else if (items > readCount) {
            //< ignored because of offline mode
            state = STATE_UPDATED
        }
    }

    var isFavorite: Boolean = false
    val idLong by lazy { id.toLong() }
    var state: Int = STATE_NEW

    @GsonExclude val date by lazy { Date(time) }

    private var _styledSubject: CharSequence? = null
    fun styledSubject(context: Context): CharSequence {
        if (_styledSubject == null) {
            _styledSubject = ZumpaSimpleParser.parseBody(subject, context)
        }
        return _styledSubject!!
    }

    var hasResponseForYou: Boolean = false
    var lastAuthor: String? = null
    var offlineItems: List<ZumpaThreadItem>? = null
}

data class ZumpaThreadItem(val author: String,
                                  val body: String,
                                  val time: Long) {
    var hasResponseForYou: Boolean = false
    var authorReal: String? = null
    var isOwnThread: Boolean? = null
    var survey: Survey? = null
    var urls: List<String>? = null
    var rating: String? = null

    val date by lazy { Date(time) }

    private var _styledBody: CharSequence? = null
    fun styledBody(context: Context): CharSequence {
        if (_styledBody == null) {
            _styledBody = ZumpaSimpleParser.parseBody(body, context)
        }
        return _styledBody!!
    }

    private var _styledAuthor: CharSequence? = null
    fun styledAuthor(context: Context): CharSequence {
        if (_styledAuthor == null) {
            if (rating.isNullOrEmpty()) {
                _styledAuthor = author
            } else {
                val r = rating!!
                val ssb = SpannableString(author + " " + r)
                val color = if (r[0] == '+') R.color.rating_good else R.color.rating_bad
                ssb.setSpan(ForegroundColorSpan(context.resources.getColor(color)), ssb.length - r.length, ssb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                _styledAuthor = ssb
            }
        }
        return _styledAuthor!!
    }
}

data class Survey(val id: String,
                         val question: String,
                         val responses: Int,
                         val items: List<SurveyItem>)

data class SurveyItem(val id: Int,
                             val surveyId: String,
                             val text: String,
                             val percents: Int,
                             var voted: Boolean)


//region parsing result
data class ZumpaMainPageResult(val prevThreadId: String?,
                                      val nextThreadId: String,
                                      val items: LinkedHashMap<String, ZumpaThread>)

data class ZumpaThreadResult(val items: List<ZumpaThreadItem>)
//endregion

/*
 List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
        nameValuePairs.add(new BasicNameValuePair(
                ZR.Constants.WebForm.LOGIN_FORM_NAME, userName));
        nameValuePairs.add(new BasicNameValuePair(
                ZR.Constants.WebForm.LOGIN_FORM_PASSWORD, password));
        nameValuePairs.add(new BasicNameValuePair(
                ZR.Constants.WebForm.LOGIN_FORM_TIMELIMIT,
                ZR.Constants.WebForm.LOGIN_FORM_TIMELIMIT_VALUE));
        nameValuePairs.add(new BasicNameValuePair(
                ZR.Constants.WebForm.LOGIN_FORM_BUTTON,
                ZR.Constants.WebForm.LOGIN_FORM_BUTTON_VALUE));
        return new UrlEncodedFormEntity(nameValuePairs);
 */

//region bodies
interface ZumpaBody {
    fun toHttpPostString(): String
}

class ZumpaLoginBody(
        val nick: String,
        val pass: String) : ZumpaBody {

    private val rem = "5"//timelimit
    private val login = "Přihlásit"

    override fun toHttpPostString(): String {
        return StringBuilder(64)
                .append("nick=").append(nick.encodeHttp())
                .append("&pass=", pass.encodeHttp())
                .append("&rem=", rem)
                .append("&login=", login.encodeHttp()).toString()
    }
}

data class ZumpaThreadBody(
        val author: String,
        val subject: String,
        val body: String,
        val threadId: String? = null
) : ZumpaBody {
    private val f: String = "2"//something
    private val a: String = "post"//postType
    private val t by lazy { threadId }//postId1
    private val p by lazy { threadId }//postId2
    private val post = "+Odeslat+"//postButton

    override fun toHttpPostString(): String {
        val sb = StringBuilder(64)
                .append("author=").append(author.encodeHttp())
                .append("&subject=", subject.encodeHttp())
                .append("&body=", body.encodeHttp())
                .append("&f=", f)
                .append("&a=", a)
                .append("&post=", post.encodeHttp())
        threadId?.let {
            sb.append("&threadId=", it.encodeHttp())
                    .append("&t=", t)
                    .append("&p=", p)
        }
        return sb.toString()
    }
}

class ZumpaVoteSurveyBody(
        val id: String,
        val item: Int) : ZumpaBody {

    override fun toHttpPostString(): String {
        val sb = StringBuilder(32)
                .append("a=").append(id)
                .append("&typ=A")
                .append("&v=").append(item)
        return sb.toString()
    }
}

class ZumpaToggleBody(
        val id: String,
        val type: String
) : ZumpaBody {
    companion object {
        val tFavorite = "F"
        val tIgnore = "I"
    }

    override fun toHttpPostString(): String {
        val sb = StringBuilder(32)
                .append("threadid=").append(id)
                .append("&typ=").append(type)
        return sb.toString()
    }
}

data class ZumpaPushMessage(val threadId: String, val from: String, val message: String?)

data class ZumpaReadState(val threadId: String, var count: Int)

open class ZumpaGenericResponse(val data: ByteArray, val contentType: String?) {
    fun asString() = String(data, Charset.forName(ZR.Constants.ENCODING))
    fun asUTFString() = String(data, Charset.forName("UTF-8"))

}

data class ZumpaWSBody(private val pages: Int = 1) : ZumpaBody {
    override fun toHttpPostString(): String {
        return "{\"Pages\" : $pages}"
    }
}
//