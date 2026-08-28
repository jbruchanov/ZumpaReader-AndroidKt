package com.scurab.android.zumpareader.test

import com.scurab.android.zumpareader.ui.mainlist.MainListUiState
import com.scurab.android.zumpareader.ui.mainlist.ThreadRowUiState
import com.scurab.android.zumpareader.model.ThreadState

fun Fixtures.MainList.uiState(rows: Int = 8) = MainListUiState(
    rows = List(rows) { index -> row(index) },
    isLoggedIn = true,
    canInteract = true,
)

fun Fixtures.MainList.empty() = MainListUiState(isLoggedIn = true, canInteract = true)

fun Fixtures.MainList.offline() = uiState(rows = 3).copy(
    isOffline = true,
    canInteract = false,
)

fun Fixtures.MainList.loading() = uiState(rows = 3).copy(isLoading = true)

fun Fixtures.MainList.row(
    index: Int = 0,
    state: ThreadState = ThreadState.entries[index % ThreadState.entries.size],
    isMenuOpen: Boolean = false,
) = ThreadRowUiState(
    id = (2_013_400 + index).toString(),
    subject = SUBJECTS[index % SUBJECTS.size],
    author = AUTHORS[index % AUTHORS.size],
    lastAuthor = if (index % 3 == 0) AUTHORS[(index + 1) % AUTHORS.size] else null,
    answerCount = (index * 7) % 143,
    time = 1_754_000_000_000L + index * 3_600_000L,
    useShortTimeFormat = index % 3 == 0,
    state = state,
    isFavorite = index % 4 == 0,
    isSelected = false,
    isMenuOpen = isMenuOpen,
)

private val SUBJECTS = listOf(
    "Kam na dovolenou? :)",
    "Nekdo tu ma zkusenost s https://zunpa.cz/necim ?",
    "Dneska to bylo fakt dobry ;)",
    "Kdo vi jak na to",
    "o_O co to melo byt",
)

private val AUTHORS = listOf("honza", "petr", "lucka", "admin", "karel")
