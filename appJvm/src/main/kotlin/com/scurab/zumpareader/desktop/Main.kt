package com.scurab.zumpareader.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.util.formatThreadListTime
import kotlinx.coroutines.launch

/**
 * The desktop entry point.
 *
 * Deliberately one screen: this module exists to prove `:shared` runs off Android, and the thread
 * list exercises the whole chain - the Ktor client on its jvm engine, the ISO-8859-2 decoding, the
 * Ksoup parser, `kotlinx-datetime` formatting and the repository - in one go.
 */
fun main() = application {
    val wiring = remember { Wiring() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "ZumpaReader (desktop)",
        state = rememberWindowState(width = 900.dp, height = 700.dp),
    ) {
        MainList(wiring)
    }
}

private sealed interface ListState {
    data object Loading : ListState
    data class Loaded(val threads: List<ZumpaThread>) : ListState
    data class Failed(val message: String) : ListState
}

@Composable
private fun MainList(wiring: Wiring) {
    var state by remember { mutableStateOf<ListState>(ListState.Loading) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        state = ListState.Loading
        state = runCatching { wiring.threads.loadMainPage(fromThread = null, filter = "0") }
            .fold(
                onSuccess = { ListState.Loaded(it.items.values.sortedByDescending { t -> t.idLong }) },
                onFailure = { ListState.Failed(it.message ?: it::class.simpleName ?: "failed") },
            )
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Žumpa", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f))
            TextButton(onClick = { scope.launch { load() } }) { Text("Reload", color = Accent) }
        }

        when (val current = state) {
            is ListState.Loading -> Centered { CircularProgressIndicator(color = Accent) }

            is ListState.Failed -> Centered {
                Text("Could not load: ${current.message}", color = Color.Red)
            }

            is ListState.Loaded -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(current.threads, key = { _, t -> t.id }) { index, thread ->
                    ThreadRow(thread, isEven = index % 2 == 0)
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: ZumpaThread, isEven: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isEven) RowEven else RowOdd)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(thread.subject, color = Color.White, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(thread.author, color = Accent, fontSize = 12.sp)
                Text(thread.time.formatThreadListTime(useShortFormat = false), color = Color.Gray, fontSize = 12.sp)
            }
        }
        Text(thread.items.toString(), color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

//the android app's palette, close enough that the two look like the same product
private val Background = Color(0xFF000000)
private val Accent = Color(0xFFF0A030)
private val RowEven = Color(0xFF000000)
private val RowOdd = Color(0xFF1A1A1A)
