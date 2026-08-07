package com.scurab.android.zumpareader.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.scurab.android.zumpareader.model.ZumpaReadState
import com.scurab.android.zumpareader.util.ZumpaPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.TreeMap

/**
 * How many messages of each thread have already been seen, which is what drives the new/updated
 * state bar on a list row. Lifted out of [com.scurab.android.zumpareader.ZumpaReaderApp] unchanged.
 */
class ZumpaReadStateRepository(private val prefs: ZumpaPrefs, private val gson: Gson) {

    private val states = TreeMap<String, ZumpaReadState>()

    private val _readStates = MutableStateFlow<Map<String, ZumpaReadState>>(emptyMap())
    val readStates: StateFlow<Map<String, ZumpaReadState>> = _readStates.asStateFlow()

    /**
     * Transitional: the raw map for the call sites that have not moved to [readStates] yet.
     * Deleted with the last of them in phase 8.
     */
    val raw: TreeMap<String, ZumpaReadState> get() = states

    init {
        load()
    }

    fun readCount(threadId: String): Int? = states[threadId]?.count

    fun markRead(threadId: String, count: Int) {
        val existing = states[threadId]
        if (existing != null) {
            existing.count = count
        } else {
            states[threadId] = ZumpaReadState(threadId, count)
        }
        publish()
    }

    private fun load() {
        val json = prefs.readStates ?: return
        val type = object : TypeToken<TreeMap<String, ZumpaReadState>>() {}.type
        val loaded: TreeMap<String, ZumpaReadState>? = gson.fromJson(json, type)
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
            val iterator = states.descendingKeySet().iterator()
            val last = iterator.next()
            var first = ""
            for (i in 1..MAX_STATES_TO_STORE) {
                first = iterator.next()
            }
            toStore = states.subMap(first, last)
        }
        prefs.readStates = gson.toJson(toStore)
    }

    private fun publish() {
        _readStates.value = LinkedHashMap(states)
    }

    private companion object {
        const val MAX_STATES_TO_STORE = 100
    }
}
