package com.scurab.zumpareader.desktop

import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin

/**
 * `./gradlew :appJvm:smoke` - loads the forum through `:shared` and prints what came back, without
 * opening a window. Not a test: it hits the live site, so it has no business failing a build.
 *
 * The code points are printed on purpose. A Windows console renders the Czech characters as `?`
 * whatever the string holds, so the text alone cannot tell a console problem from a decoding one.
 */
fun main() = runBlocking {
    val koin = startKoin { modules(desktopModule()) }.koin
    val result = koin.get<ZumpaThreadRepository>().loadMainPage(fromThread = null, filter = "0")
    println("SMOKE threads=${result.items.size} next=${result.nextThreadId}")
    result.items.values.take(3).forEach {
        println("SMOKE row id=${it.id} items=${it.items} author=${it.author} subject=${it.subject}")
    }
    //code points, so a mangled console cannot be mistaken for a mangled decode
    val diacritic = result.items.values.firstOrNull { t -> t.subject.any { it.code > 127 } }
    if (diacritic != null) {
        val codes = diacritic.subject.toList()
            .filter { it.code > 127 }
            .joinToString(" ") { "U+%04X".format(it.code) }
        println("SMOKE non-ascii-codepoints=$codes")
    }
}
