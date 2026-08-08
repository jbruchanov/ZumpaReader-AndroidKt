package com.scurab.android.zumpareader.model

import com.scurab.android.zumpareader.util.decodeLatin2
import com.scurab.android.zumpareader.util.encodeHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

        //the JsonObject readers that used to live here are now
        //com.scurab.android.zumpareader.data.OfflineThreadDto and ZumpaWsThreadDto
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

/**
 * Persisted as JSON in the preferences, so the keys name themselves explicitly and do not follow
 * whatever the obfuscator renames the properties to.
 */
@Serializable
data class ZumpaReadState(
    @SerialName("threadId") val threadId: String,
    @SerialName("count") var count: Int,
)

/**
 * A response nothing wants parsed. [status] and [setCookies] are here because the endpoints that
 * return one of these are exactly the endpoints that care about a 302 or about the login cookies -
 * they used to reach for retrofit's `Response<T>` to get at them.
 */
open class ZumpaGenericResponse(
    val data: ByteArray,
    val contentType: String?,
    val status: Int = 200,
    val setCookies: List<String> = emptyList(),
) {
    /** The forum's own charset - see [com.scurab.android.zumpareader.util.decodeLatin2]. */
    fun asString() = data.decodeLatin2()
    fun asUTFString() = data.decodeToString()
}

data class ZumpaWSBody(private val pages: Int = 1) : ZumpaBody {
    override fun toHttpPostString(): String {
        return "{\"Pages\" : $pages}"
    }
}
//