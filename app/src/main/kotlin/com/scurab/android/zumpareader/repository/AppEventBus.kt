package com.scurab.android.zumpareader.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The genuinely one-shot half of otto's replacement. Only cross-screen signals that must not be
 * replayed to a late subscriber belong here - anything a screen needs to know the current value of
 * is a StateFlow on a repository instead.
 */
sealed interface AppEvent {
    /** The offline download finished or was cleared, whoever shows a list should reload. */
    data object OfflineDataChanged : AppEvent

    /** A thread or an answer was posted, whatever list is up is now stale. */
    data object ContentPosted : AppEvent
}

class AppEventBus {

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = EXTRA_BUFFER)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    fun emit(event: AppEvent) {
        _events.tryEmit(event)
    }

    private companion object {
        const val EXTRA_BUFFER = 8
    }
}
