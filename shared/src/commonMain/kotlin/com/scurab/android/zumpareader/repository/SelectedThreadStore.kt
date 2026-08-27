package com.scurab.android.zumpareader.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which thread the detail pane shows while there are two panes - one half of otto's replacement.
 *
 * `LoadThreadEvent` was never really an event, it was selection state that happened to be delivered
 * as one, which is why a re-subscribing fragment could not tell what was already selected. With one
 * pane the thread arrives as a nav argument instead and nothing writes here.
 */
class SelectedThreadStore {

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    /**
     * False while the selection is only the thread the list opened itself on, which is a reasonable
     * thing to show in a pane that would otherwise be empty and a very unreasonable thing to
     * navigate to. Losing the pane on a rotation must not open a thread nobody picked.
     */
    var isExplicit: Boolean = false
        private set

    fun select(threadId: String, explicit: Boolean = true) {
        _selected.value = threadId
        isExplicit = explicit
    }

    /**
     * Nothing is selected any more, so no row is lit.
     *
     * Selection only means something while there are two panes - it says "this is the one the pane
     * is showing". Dropping to one pane turns that thread into a screen of its own, and a selection
     * that outlived the pane would leave a row highlighted in the list behind it for no reason
     * anybody could see. Called when the second pane goes away, once the thread has been handed to
     * the back stack.
     */
    fun clear() {
        _selected.value = null
        isExplicit = false
    }
}
