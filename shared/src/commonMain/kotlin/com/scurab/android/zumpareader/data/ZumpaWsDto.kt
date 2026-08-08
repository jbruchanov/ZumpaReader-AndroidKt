package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.util.looksLikeImageUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `/zumpa` web service response - a different shape from the on-disk snapshot in
 * [OfflineThreadDto], PascalCase and with its own field names, which is why it gets its own DTOs
 * instead of one being bent to fit the other.
 *
 * Every property names itself with [SerialName]: these keys come off the wire and must not follow
 * the obfuscator's renaming of the properties.
 */
@Serializable
data class ZumpaWsResponseDto(
    @SerialName("Context") val context: ZumpaWsContextDto,
)

@Serializable
data class ZumpaWsContextDto(
    @SerialName("Items") val items: List<ZumpaWsThreadDto> = emptyList(),
)

@Serializable
data class ZumpaWsThreadDto(
    @SerialName("ID") val id: String,
    @SerialName("Subject") val subject: String,
    @SerialName("Time") val time: Long = 0L,
    @SerialName("Author") val author: String = "",
    @SerialName("HasRespondForYou") val hasResponseForYou: Boolean = false,
    @SerialName("Items") val items: List<ZumpaWsItemDto> = emptyList(),
)

@Serializable
data class ZumpaWsItemDto(
    @SerialName("AuthorReal") val authorReal: String,
    @SerialName("AuthorFake") val authorFake: String? = null,
    @SerialName("Body") val body: String,
    @SerialName("Time") val time: Long = 0L,
    @SerialName("InsideUris") val insideUris: List<String>? = null,
)

fun ZumpaWsThreadDto.toDomain(): ZumpaThread = ZumpaThread(id, subject).apply {
    time = this@toDomain.time
    author = this@toDomain.author
    hasResponseForYou = this@toDomain.hasResponseForYou
    //qualified: inside apply, a bare `items` is ZumpaThread's Int count, not the dto's list
    offlineItems = this@toDomain.items.map { it.toDomain() }
}

fun ZumpaWsItemDto.toDomain(): ZumpaThreadItem =
    //`authorReal` on both sides is not a slip in the port - the gson version read "AuthorReal" in
    //both branches of its `has("AuthorFake")` check, so the fake name has never been used. Kept as
    //it was so this stays a dependency swap; see the note in KMP_PLAN.md.
    ZumpaThreadItem(authorReal, body, time).apply {
        this.authorReal = this@toDomain.authorReal
        urls = insideUris
    }

/** Only the image urls are worth prefetching for offline use. */
fun List<ZumpaThread>.offlineImageUrls(): Set<String> {
    val urls = LinkedHashSet<String>()
    forEach { thread ->
        thread.offlineItems?.forEach { item ->
            item.urls?.forEach { url ->
                if (url.looksLikeImageUrl()) {
                    urls += url
                }
            }
        }
    }
    return urls
}
