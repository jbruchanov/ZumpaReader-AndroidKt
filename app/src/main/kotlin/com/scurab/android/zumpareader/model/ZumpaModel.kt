package com.scurab.android.zumpareader.model

import java.util.*

/**
 * Created by JBruchanov on 24/11/2015.
 */

public data class ZumpaThread
public constructor(val id: String,
                    var subject: CharSequence) {

    public constructor(id: String,
                subject: CharSequence,
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
    var threads: Int = 0
    var isFavorite: Boolean = false
    val idLong by lazy { id.toLong() }

    public val date by lazy { Date(time) }
}

public data class ZumpaThreadItem(val author: CharSequence,
                                  val body: CharSequence,
                                  val time: Long,
                                  val urls: List<String>?) {
    public var hasResponseForYou: Boolean = false
    public var authorReal: String? = null
    public var isOwnThread: Boolean? = null
    public var survey: Survey? = null

    public val date by lazy { Date(time) }
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