package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.util.looksLikeImageUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The offline snapshot on disk - `offline.json`, written by
 * [com.scurab.android.zumpareader.usecase.OfflineDownloadUseCase] and read back by
 * [com.scurab.android.zumpareader.repository.OfflineDataRepository].
 *
 * Explicit on-disk shape, where gson used to reflect over [ZumpaThread]'s fields and needed a
 * `GsonExcludeStrategy` to keep the derived ones out. Two consequences worth knowing:
 *
 * - **Every property names itself with [SerialName].** The class names and property names are
 *   whatever the obfuscator decides; the JSON keys are not allowed to move with them.
 * - **The names match what gson emitted**, `_items` and all, so snapshots written by older builds
 *   still load. `_items` was the private backing field behind `ZumpaThread.items`; the reader has
 *   always recomputed the count from `offlineItems` and ignored it, and still does.
 */
@Serializable
data class OfflineThreadDto(
    @SerialName("id") val id: String,
    @SerialName("subject") val subject: String,
    @SerialName("author") val author: String = "",
    @SerialName("contentUrl") val contentUrl: String = "",
    @SerialName("time") val time: Long = 0L,
    @SerialName("_items") val itemCount: Int = 0,
    @SerialName("isFavorite") val isFavorite: Boolean = false,
    @SerialName("state") val state: Int = ZumpaThread.STATE_NEW,
    @SerialName("hasResponseForYou") val hasResponseForYou: Boolean = false,
    @SerialName("lastAuthor") val lastAuthor: String? = null,
    @SerialName("offlineItems") val offlineItems: List<OfflineThreadItemDto>? = null,
)

@Serializable
data class OfflineThreadItemDto(
    @SerialName("author") val author: String,
    @SerialName("body") val body: String,
    @SerialName("time") val time: Long,
    @SerialName("authorReal") val authorReal: String? = null,
    @SerialName("hasResponseForYou") val hasResponseForYou: Boolean = false,
    @SerialName("isOwnThread") val isOwnThread: Boolean? = null,
    @SerialName("urls") val urls: List<String>? = null,
    @SerialName("rating") val rating: String? = null,
)

/**
 * What `ZumpaThread.thread(JsonObject)` did, unchanged: `contentUrl`, `isFavorite` and
 * `hasResponseForYou` are deliberately *not* restored - the old reader never read them either - the
 * item count is derived from the items rather than taken from the file, and the state is reset.
 */
fun OfflineThreadDto.toDomain(): ZumpaThread = ZumpaThread(id, subject).apply {
    author = this@toDomain.author
    time = this@toDomain.time
    lastAuthor = this@toDomain.lastAuthor
    offlineItems = this@toDomain.offlineItems.orEmpty().map { it.toDomain() }
    items = maxOf(0, (offlineItems?.count() ?: 0) - 1)
    state = ZumpaThread.STATE_NONE
}

fun OfflineThreadItemDto.toDomain(): ZumpaThreadItem =
    ZumpaThreadItem(author, body, time).apply {
        authorReal = this@toDomain.authorReal
        urls = this@toDomain.urls
        hasResponseForYou = this@toDomain.hasResponseForYou
        isOwnThread = this@toDomain.isOwnThread
        rating = this@toDomain.rating
    }

fun ZumpaThread.toDto(): OfflineThreadDto = OfflineThreadDto(
    id = id,
    subject = subject,
    author = author,
    contentUrl = contentUrl,
    time = time,
    itemCount = items,
    isFavorite = isFavorite,
    state = state,
    hasResponseForYou = hasResponseForYou,
    lastAuthor = lastAuthor,
    offlineItems = offlineItems?.map { it.toDto() },
)

fun ZumpaThreadItem.toDto(): OfflineThreadItemDto = OfflineThreadItemDto(
    author = author,
    body = body,
    time = time,
    authorReal = authorReal,
    hasResponseForYou = hasResponseForYou,
    isOwnThread = isOwnThread,
    urls = urls,
    rating = rating,
)

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
