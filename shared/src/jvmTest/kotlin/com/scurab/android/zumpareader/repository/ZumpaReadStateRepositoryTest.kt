package com.scurab.android.zumpareader.repository

import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.util.InMemoryKeyValueStore
import com.scurab.android.zumpareader.util.ZumpaPrefs
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * How many messages of a thread have been seen, which is the whole of the new/updated bar on a list
 * row.
 *
 * The arithmetic lives here rather than at the callers because there are two of them now - the
 * phone's thread screen and the desktop's detail pane - and an off-by-one kept in two places is one
 * that drifts.
 */
class ZumpaReadStateRepositoryTest {

    private val repository = ZumpaReadStateRepository(ZumpaPrefs(InMemoryKeyValueStore()), Json)

    private fun messages(count: Int) =
        List(count) { ZumpaThreadItem(author = "someone", body = "body $it", time = 0L) }

    @Test
    fun `the opening post is not one of the answers`() {
        repository.markRead("1", messages(3))

        assertEquals(2, repository.readCount("1"))
    }

    /** A thread cannot have been read a negative number of times whatever the forum answered. */
    @Test
    fun `a thread that came back empty counts as nothing read`() {
        repository.markRead("1", emptyList())

        assertEquals(0, repository.readCount("1"))
    }

    /**
     * The count used to be written straight through a `var` on the stored object, which left the
     * map being published holding the very instances the previous one held - so the two compared
     * equal and the `StateFlow` dropped the change on the floor. Nothing collected it at the time.
     * The thread list does now, and this is the whole reason it can be told a thread was read.
     */
    @Test
    fun `a changed count reaches a collector`() = runTest {
        repository.markRead("1", 2)
        val seen = mutableListOf<Int?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.readStates.collect { seen += it["1"]?.count }
        }

        repository.markRead("1", 5)
        collector.cancel()

        assertEquals(listOf(2, 5), seen)
    }

    /** Reopening a thread nothing has been added to should not stir the list behind it. */
    @Test
    fun `a count that has not changed is not published again`() = runTest {
        repository.markRead("1", 2)
        val seen = mutableListOf<Int?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.readStates.collect { seen += it["1"]?.count }
        }

        repository.markRead("1", 2)
        collector.cancel()

        assertEquals(listOf(2), seen)
    }
}
