package com.scurab.android.zumpareader.test

import com.scurab.android.zumpareader.ui.sublist.DraftUiState
import com.scurab.android.zumpareader.ui.sublist.SubListRowUiState
import com.scurab.android.zumpareader.ui.sublist.SubListUiState
import com.scurab.android.zumpareader.ui.sublist.SurveyItemUiState
import com.scurab.android.zumpareader.ui.sublist.SurveyUiState

fun Fixtures.SubList.uiState() = SubListUiState(
    threadId = "2013474",
    title = "Kam na dovolenou? :)",
    rows = listOf(
        message(index = 0, body = "Nekdo tip na neco levneho v cervnu? :)"),
        SubListRowUiState.Link(itemIndex = 0, url = "https://zunpa.cz/neco/dlouhy/odkaz"),
        message(index = 1, author = "petr", body = "Ja bych zkusil Chorvatsko o_O"),
        SubListRowUiState.Image(itemIndex = 1, url = "https://zunpa.cz/fotodisk/beach.jpg"),
        message(index = 2, author = "lucka", rating = "+3", body = "honza » to zni dobre ;)"),
    ),
    canPost = true,
)

fun Fixtures.SubList.withSurvey() = uiState().let { state ->
    state.copy(rows = listOf(state.rows.first(), SubListRowUiState.Survey(0, survey())) + state.rows.drop(1))
}

fun Fixtures.SubList.replying() = uiState().copy(
    isPostPanelVisible = true,
    draft = DraftUiState(headers = listOf("@petr: \n"), body = "jo taky se mi to libi"),
)

fun Fixtures.SubList.sending() = replying().copy(isSending = true)

fun Fixtures.SubList.message(
    index: Int = 0,
    author: String = "honza",
    rating: String? = null,
    body: String = "nejaky text",
    isMenuOpen: Boolean = false,
) = SubListRowUiState.Message(
    itemIndex = index,
    author = author,
    authorReal = author,
    rating = rating,
    body = body,
    time = 1_754_000_000_000L + index * 60_000L,
    isMenuOpen = isMenuOpen,
)

fun Fixtures.SubList.survey() = SurveyUiState(
    id = "s1",
    question = "Kam pojedete?",
    responses = 42,
    items = listOf(
        SurveyItemUiState(1, "s1", "Chorvatsko", 55, true),
        SurveyItemUiState(2, "s1", "Italie", 30, false),
        SurveyItemUiState(3, "s1", "Nikam", 15, false),
    ),
)
