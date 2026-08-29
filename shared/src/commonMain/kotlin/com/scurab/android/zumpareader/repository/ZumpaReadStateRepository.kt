package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.model.ZumpaReadState
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.util.ZumpaPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * How many messages of each thread have already been seen, which is what drives the new/updated
 * state bar on a list row. Lifted out of [com.scurab.android.zumpareader.ZumpaReaderApp] unchanged.
 */
class ZumpaReadStateRepository(private val prefs: ZumpaPrefs, private val json: Json) {

    private val states = LinkedHashMap<String, ZumpaReadState>()

    private val _readStates = MutableStateFlow<Map<String, ZumpaReadState>>(emptyMap())
    val readStates: StateFlow<Map<String, ZumpaReadState>> = _readStates.asStateFlow()

    init {
        load()
    }

    fun readCount(threadId: String): Int? = states[threadId]?.count

    /**
     * Everything a thread currently holds has been seen.
     *
     * The opening post is not an answer and is not counted, which is the arithmetic the list rows
     * are compared against. The rule lives here rather than at each caller because there are two of
     * them - the phone and the desktop - and an off-by-one kept in two places is one that drifts.
     */
    fun markRead(threadId: String, items: List<ZumpaThreadItem>) =
        markRead(threadId, maxOf(0, items.size - 1))

    fun markRead(threadId: String, count: Int) {
        if (states[threadId]?.count == count) return
        //replaced rather than written through. The count used to be a `var` assigned in place,
        //which left the map published below holding the very objects the previous value held - so
        //the new map compared equal to the old and the StateFlow dropped the change. Nothing
        //collected it at the time; MainListViewModel does now.
        states[threadId] = ZumpaReadState(threadId, count)
        publish()
    }

    private fun load() {
        val stored = prefs.readStates ?: return
        //a malformed or truncated value used to come back as null from gson and be ignored; the
        //serializer throws instead, and losing the read states is not worth a crash on startup
        val loaded = runCatching {
            json.decodeFromString<Map<String, ZumpaReadState>>(stored)
        }.getOrNull()
        loaded?.let {
            states.clear()
            states.putAll(it)
        }
        publish()
    }

    /**
     * Called when the last activity stops. The trimming is the original implementation verbatim,
     * including `subMap(first, last)` excluding the newest entry.
     */
    fun persist() {
        var toStore: Map<String, ZumpaReadState> = states
        if (states.size > MAX_STATES_TO_STORE) {
            //`TreeMap.descendingKeySet()` and `subMap(first, last)` spelled out, since neither
            //exists in common code. `last` was the newest key and `first` the one
            //MAX_STATES_TO_STORE places below it; `subMap` is half-open, hence `< last`.
            val descending = states.keys.sortedDescending()
            val last = descending.first()
            val first = descending[MAX_STATES_TO_STORE]
            toStore = states
                .filterKeys { it >= first && it < last }
                .toList()
                .sortedBy { (key, _) -> key }
                .toMap()
        }
        prefs.readStates = json.encodeToString<Map<String, ZumpaReadState>>(toStore)
    }

    private fun publish() {
        _readStates.value = LinkedHashMap(states)
    }

    private companion object {
        const val MAX_STATES_TO_STORE = 100
    }
}
