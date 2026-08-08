package com.scurab.android.zumpareader.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which thread the detail pane shows on a tablet - one half of otto's replacement.
 *
 * `LoadThreadEvent` was never really an event, it was selection state that happened to be delivered
 * as one, which is why a re-subscribing fragment could not tell what was already selected. On a
 * phone nothing writes here, so the detail screen collects the same flow on both form factors.
 */
class SelectedThreadStore {

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    fun select(threadId: String) {
        _selected.value = threadId
    }
}
