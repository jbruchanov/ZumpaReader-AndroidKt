package com.scurab.zumpareader.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.arch.WindowLayout
import com.scurab.android.zumpareader.usecase.OfflineProgress
import kotlinx.coroutines.launch

/**
 * The desktop entry point.
 *
 * Two panes, always: a desktop window is never the narrow case the Android app switches layout for,
 * so there is nothing to decide - the list on the left, whatever is selected on the right. That is
 * the tablet layout of `:appAndroid`, which is why this has no navigation of any kind.
 *
 * The UI is not shared with Android - `:appAndroid` is on `androidx.compose` and this is on Compose
 * Multiplatform - so this is a second, smaller implementation over the same `:shared` stack rather
 * than the same screens. Merging them is phase 3 in `KMP_PLAN.md`.
 */
fun main() = application {
    val wiring = remember { Wiring() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "ZumpaReader (desktop)",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        App(wiring)
    }
}

/** Loading, loaded or failed - the three states every call in here can be in. */
internal sealed interface Loadable {
    data object Loading : Loadable
    data object Loaded : Loadable
    data class Failed(val message: String) : Loadable
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(wiring: Wiring) {
    val scope = rememberCoroutineScope()
    val isOffline by wiring.settings.isOffline.collectAsState()
    val isLoggedIn by wiring.settings.isLoggedIn.collectAsState()

    var threads by remember { mutableStateOf<List<ZumpaThread>>(emptyList()) }
    var listState by remember { mutableStateOf<Loadable>(Loadable.Loading) }
    var nextThreadId by remember { mutableStateOf<String?>(null) }
    var isAppending by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var isLoginOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        listState = Loadable.Loading
        runCatching {
            wiring.threads.loadMainPage(fromThread = null, filter = wiring.settings.filter.value)
        }.onSuccess { result ->
            threads = result.items.values.sortedByDescending { it.idLong }
            nextThreadId = result.nextThreadId.takeIf { it.isNotEmpty() }
            listState = Loadable.Loaded
        }.onFailure {
            listState = Loadable.Failed(it.message ?: it::class.simpleName ?: "failed")
        }
    }

    /**
     * The page after the one on screen. The list had no paging at all: `nextThreadId` came back on
     * every result and was thrown away, so the bottom of the first page was the end of the forum.
     */
    suspend fun appendNextPage() {
        val from = nextThreadId ?: return
        if (isAppending) return
        isAppending = true
        runCatching {
            wiring.threads.loadMainPage(fromThread = from, filter = wiring.settings.filter.value)
        }.onSuccess { result ->
            threads = (threads + result.items.values)
                .distinctBy { it.id }
                .sortedByDescending { it.idLong }
            nextThreadId = result.nextThreadId.takeIf { it.isNotEmpty() }
        }.onFailure {
            status = it.message ?: "Could not load more"
        }
        isAppending = false
    }

    suspend fun download() {
        status = "Downloading..."
        runCatching {
            var downloaded: LinkedHashMap<String, ZumpaThread>? = null
            wiring.downloader.run(
                pages = DOWNLOAD_PAGES,
                downloadImages = false,
                outJsonFile = wiring.offlineSnapshotPath,
            ).collect { progress ->
                when (progress) {
                    is OfflineProgress.Threads ->
                        status = "Downloading ${progress.done}/${progress.total}"
                    is OfflineProgress.Done -> downloaded = progress.data
                    is OfflineProgress.Images -> Unit
                }
            }
            downloaded
        }.onSuccess { data ->
            //an empty result is a failed download, not a new snapshot - as on Android
            if (data.isNullOrEmpty()) {
                status = "Download failed"
            } else {
                wiring.applyDownloaded(data)
                status = "Downloaded ${data.size} threads"
                reload()
            }
        }.onFailure {
            status = it.message ?: "Download failed"
        }
    }

    //the switch changes where the list comes from, so the list has to be read again
    LaunchedEffect(isOffline) { reload() }

    /*
     * One pane or two, off the width of the window rather than the fact that it is a desktop.
     *
     * A window is not a device: it opens wide enough for two panes and can be dragged narrower than
     * either of them is worth, and at that point two panes of three hundred points each are worse
     * than one. Same threshold as Android - `WindowLayout.TWO_PANE_MIN_WIDTH_DP`, read out of
     * `:shared` so the two apps cannot drift on the number - and measured with BoxWithConstraints,
     * which recomposes as the window is dragged without anything having to listen for it.
     *
     * Narrow, the selected thread is the whole window and the bar grows a way back to the list.
     * Selection is kept either way, so widening the window again shows it in the second pane.
     */
    BoxWithConstraints(Modifier.fillMaxSize().background(Background)) {
        val isTwoPane = maxWidth >= WindowLayout.TWO_PANE_MIN_WIDTH_DP.dp
        val isShowingDetail = !isTwoPane && selected != null

        Column(Modifier.fillMaxSize()) {
            //Sticky, never collapsing. A desktop window has no shortage of height to reclaim, and
            //a bar that moved while the wheel turned would be something to chase with the pointer.
            TopAppBar(
                title = {
                    Text(
                        text = if (isOffline) "Zumpa (offline)" else "Zumpa",
                        color = Accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                navigationIcon = {
                    if (isShowingDetail) {
                        TextButton(onClick = { selected = null }) {
                            Text("< List", color = Accent)
                        }
                    }
                },
                actions = {
                    if (isAppending || listState is Loadable.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 12.dp).size(18.dp),
                            color = Accent,
                            strokeWidth = 2.dp,
                        )
                    }
                    OverflowMenu(
                        isOffline = isOffline,
                        isLoggedIn = isLoggedIn,
                        onReload = { scope.launch { reload() } },
                        onLogin = { isLoginOpen = true },
                        onLogout = {
                            scope.launch {
                                wiring.auth.logout()
                                status = "Signed out"
                                reload()
                            }
                        },
                        onToggleOffline = { wiring.setOffline(!isOffline) },
                        onDownload = { scope.launch { download() } },
                    )
                },
            )

            if (isTwoPane) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(LIST_WEIGHT)) {
                        ThreadList(
                            state = listState,
                            threads = threads,
                            selectedId = selected,
                            hasMore = nextThreadId != null,
                            onSelect = { selected = it },
                            onEndReached = { scope.launch { appendNextPage() } },
                            onRetry = { scope.launch { reload() } },
                        )
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(DividerColor))
                    Box(Modifier.weight(DETAIL_WEIGHT)) {
                        ThreadDetail(wiring, selected)
                    }
                }
            } else if (isShowingDetail) {
                ThreadDetail(wiring, selected)
            } else {
                ThreadList(
                    state = listState,
                    threads = threads,
                    selectedId = selected,
                    hasMore = nextThreadId != null,
                    onSelect = { selected = it },
                    onEndReached = { scope.launch { appendNextPage() } },
                    onRetry = { scope.launch { reload() } },
                )
            }
        }
    }

    status?.let { StatusToast(it) { status = null } }

    if (isLoginOpen) {
        LoginDialog(
            onDismiss = { isLoginOpen = false },
            onSubmit = { user, password ->
                isLoginOpen = false
                scope.launch {
                    status = "Signing in..."
                    runCatching { wiring.auth.login(user, password) }
                        .onSuccess {
                            status = if (it.isLoggedIn) "Signed in as $user" else "Sign in refused"
                        }
                        .onFailure { status = it.message ?: "Sign in failed" }
                    reload()
                }
            },
        )
    }
}

/** Enough of the forum to be worth having offline without making the user wait for all of it. */
private const val DOWNLOAD_PAGES = 3

private const val LIST_WEIGHT = 0.4f
private const val DETAIL_WEIGHT = 0.6f

//the android app's palette, close enough that the two look like the same product
internal val Background = Color(0xFF000000)
internal val Accent = Color(0xFFFFA710)
internal val RowEven = Color(0xFF000000)
internal val RowOdd = Color(0xFF1A1A1A)
internal val DividerColor = Color(0x40FFA710)
internal val SelectedRow = Color(0x30FFA710)
