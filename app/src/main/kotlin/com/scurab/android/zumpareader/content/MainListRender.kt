package com.scurab.android.zumpareader.content

import com.scurab.android.zumpareader.text.ZumpaTextRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A row with everything already turned into what the view holder assigns straight to a widget.
 *
 * This is produced from [ThreadRowUiState] on a background dispatcher, which is what moves the span
 * building and the date formatting off the main thread - the last item of UPGRADE_PLAN.md section D.
 * In the compose phase this whole step disappears into the composable.
 */
data class RenderedThreadRow(
    val id: String,
    val subject: CharSequence,
    val author: String,
    val lastAuthor: String?,
    val answerCount: String,
    val time: String,
    val state: ThreadState,
    val isFavorite: Boolean,
    val isSelected: Boolean,
)

class MainListRender(private val text: ZumpaTextRenderer<CharSequence>) {

    private val dateFormat = SimpleDateFormat("dd.MM. HH:mm.ss", Locale.US)
    private val shortDateFormat = SimpleDateFormat("HH:mm", Locale.US)

    fun rows(rows: List<ThreadRowUiState>): List<RenderedThreadRow> = rows.map { row ->
        RenderedThreadRow(
            id = row.id,
            subject = text.subject(row.subject),
            author = row.author,
            lastAuthor = row.lastAuthor,
            answerCount = row.answerCount.toString(),
            time = (if (row.useShortTimeFormat) shortDateFormat else dateFormat).format(Date(row.time)),
            state = row.state,
            isFavorite = row.isFavorite,
            isSelected = row.isSelected,
        )
    }
}
