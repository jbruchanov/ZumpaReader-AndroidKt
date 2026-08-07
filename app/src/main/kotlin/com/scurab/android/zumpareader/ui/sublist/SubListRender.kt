package com.scurab.android.zumpareader.ui.sublist

import com.scurab.android.zumpareader.text.ZumpaTextRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [SubListRowUiState] with the markup and the timestamps already turned into what the view holder
 * assigns to a widget. Produced on a background dispatcher - this is the screen where the span
 * building used to be done on the main thread "on purpose".
 */
sealed interface RenderedSubListRow {
    val itemIndex: Int

    data class Message(
        override val itemIndex: Int,
        val author: CharSequence,
        val body: CharSequence,
        val time: String,
        /** The unrendered originals, for the copy and quote menu actions. */
        val rawAuthor: String,
        val rawAuthorReal: String?,
        val rawBody: String,
    ) : RenderedSubListRow

    data class Link(override val itemIndex: Int, val url: String) : RenderedSubListRow

    data class Image(override val itemIndex: Int, val url: String) : RenderedSubListRow

    data class Survey(
        override val itemIndex: Int,
        val survey: SurveyUiState,
    ) : RenderedSubListRow
}

class SubListRender(private val text: ZumpaTextRenderer<CharSequence>) {

    private val dateFormat = SimpleDateFormat("HH:mm.ss", Locale.US)

    /** The thread subject as the toolbar shows it - icons align to the baseline there. */
    fun titleOf(markup: String): CharSequence = text.title(markup)

    fun rows(rows: List<SubListRowUiState>): List<RenderedSubListRow> = rows.map { row ->
        when (row) {
            is SubListRowUiState.Message -> RenderedSubListRow.Message(
                itemIndex = row.itemIndex,
                author = text.author(row.author, row.rating),
                body = text.body(row.body),
                time = dateFormat.format(Date(row.time)),
                rawAuthor = row.author,
                rawAuthorReal = row.authorReal,
                rawBody = row.body,
            )

            is SubListRowUiState.Link -> RenderedSubListRow.Link(row.itemIndex, row.url)
            is SubListRowUiState.Image -> RenderedSubListRow.Image(row.itemIndex, row.url)
            is SubListRowUiState.Survey -> RenderedSubListRow.Survey(row.itemIndex, row.survey)
        }
    }
}
