package com.scurab.android.zumpareader.model

import android.content.Context
import android.content.res.Resources
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.encodeHttp
import com.scurab.android.zumpareader.util.exec
import com.squareup.okhttp.MediaType
import java.util.*

/**
 * Created by JBruchanov on 24/11/2015.
 */

public data class ZumpaThread
public constructor(val id: String,
                   var subject: String) {

    companion object{
        public val STATE_NONE = 0
        public val STATE_NEW = 1
        public val STATE_UPDATED = 2
        public val STATE_OWN = 3
    }
    public constructor(id: String,
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
            if ("JtS".equals(author)) {
                state = STATE_OWN
            } else if (state == STATE_NONE && _items != 0 && value > _items) {
                state = STATE_UPDATED
            } else if (_items == value) {
                //no update
            }
            _items = value
        }
    var isFavorite: Boolean = false
    val idLong by lazy { id.toLong() }
    var state: Int = STATE_NEW

    public val date by lazy { Date(time) }

    private var _styledSubject: CharSequence? = null
    public fun styledSubject(context: Context): CharSequence {
        if (_styledSubject == null) {
            _styledSubject = ZumpaSimpleParser.parseBody(subject, context)
        }
        return _styledSubject!!
    }
}

public data class ZumpaThreadItem(val author: CharSequence,
                                  val body: String,
                                  val time: Long) {
    public var hasResponseForYou: Boolean = false
    public var authorReal: String? = null
    public var isOwnThread: Boolean? = null
    public var survey: Survey? = null
    public var urls: List<String>? = null

    public val date by lazy { Date(time) }

    private var _styledBody: CharSequence? = null
    public fun styledBody(context: Context): CharSequence {
        if (_styledBody == null) {
            _styledBody = ZumpaSimpleParser.parseBody(body, context)
        }
        return _styledBody!!
    }
}

public data class Survey(val id: String,
                         val question: String,
                         val responses: Int,
                         val items: List<SurveyItem>)

public data class SurveyItem(val text: String,
                             val percents: Int,
                             var voted: Boolean)


//region parsing result
public data class ZumpaMainPageResult(val prevPage: String?,
                                      val nextPage: String,
                                      val items: LinkedHashMap<String, ZumpaThread>)

public data class ZumpaThreadResult(val items: List<ZumpaThreadItem>)
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
public interface ZumpaBody {
    public fun toHttpPostString(): String
}

public class ZumpaLoginBody(
        val nick: String,
        val pass: String) : ZumpaBody {

    val rem = "5"//timelimit
    val login = "Přihlásit"

    override fun toHttpPostString(): String {
        return StringBuilder(64)
                .append("nick=").append(nick.encodeHttp())
                .append("&pass=", pass.encodeHttp())
                .append("&rem=", rem)
                .append("&login=", login.encodeHttp()).toString()
    }
}

public data class ZumpaThreadBody(
        val author: String,
        val subject: String,
        val body: String,
        val threadId: String? = null
) : ZumpaBody {
    val f: String = "2"//something
    val a: String = "post"//postType
    val t by lazy { threadId }//postId1
    val p by lazy { threadId }//postId2
    val post = "Odeslat"//postButton

    override fun toHttpPostString(): String {
        val sb = StringBuilder(64)
                .append("author=").append(author.encodeHttp())
                .append("&subject=", subject.encodeHttp())
                .append("&body=", body.encodeHttp())
                .append("&f=", f)
                .append("&a=", a)
                .append("&post=", post)
        threadId.exec {
            sb.append("&threadId=", it.encodeHttp())
                    .append("&t=", t)
                    .append("&p=", p)
        }
        return sb.toString()
    }
}

public class ZumpaResponse(val data: ByteArray, val mediaType: MediaType) {

    public fun asString() = String(data, "utf-8")
}

//