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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.repository.AuthRepository
import com.scurab.android.zumpareader.repository.OfflineDataRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.model.ZumpaThreadBody
import com.scurab.android.zumpareader.arch.WindowLayout
import com.scurab.android.zumpareader.usecase.OfflineDownloadUseCase
import com.scurab.android.zumpareader.usecase.OfflineProgress
import com.scurab.android.zumpareader.util.ZumpaPrefs
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.context.startKoin

/**
 * The desktop entry point.
 *
 * The graph is built before the window, so anything the ui resolves is already there - see
 * [desktopModule], which is the same shape as `:appAndroid`'s koin module.
 *
 * The UI is not shared with Android - `:appAndroid` is on `androidx.compose` and this is on Compose
 * Multiplatform - so this is a second, smaller implementation over the same `:shared` stack rather
 * than the same screens. Merging them is phase 3 in `KMP_PLAN.md`.
 */
fun main() {
    startKoin { modules(desktopModule()) }
    application {
        Window(
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
            onCloseRequest = ::exitApplication,
            title = "ZumpaReader (desktop)",
        ) {
            DesktopTheme { App() }
        }
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
private fun App() {
    val scope = rememberCoroutineScope()
    //resolved out of composition, which is what koin-compose is here for - there is no Activity to
    //hang an injector off and no ViewModels to scope anything to
    val settings = koinInject<ZumpaSettingsRepository>()
    val threadsRepo = koinInject<ZumpaThreadRepository>()
    val auth = koinInject<AuthRepository>()
    val downloader = koinInject<OfflineDownloadUseCase>()
    val offlineData = koinInject<OfflineDataRepository>()
    val prefs = koinInject<ZumpaPrefs>()
    val isOffline by settings.isOffline.collectAsState()
    val isLoggedIn by settings.isLoggedIn.collectAsState()

    var threads by remember { mutableStateOf<List<ZumpaThread>>(emptyList()) }
    var listState by remember { mutableStateOf<Loadable>(Loadable.Loading) }
    var nextThreadId by remember { mutableStateOf<String?>(null) }
    var isAppending by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var isLoginOpen by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var isNewThreadOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        listState = Loadable.Loading
        runCatching {
            threadsRepo.loadMainPage(fromThread = null, filter = settings.filter.value)
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
            threadsRepo.loadMainPage(fromThread = from, filter = settings.filter.value)
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
            downloader.run(
                pages = DOWNLOAD_PAGES,
                downloadImages = false,
                outJsonFile = offlineData.path,
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
                //what the download landing does: the api serves it, and the list is rebuilt from it
                offlineData.setData(data)
                threadsRepo.replaceAll(data)
                status = "Downloaded ${data.size} threads"
                reload()
            }
        }.onFailure {
            status = it.message ?: "Download failed"
        }
    }

    /** A new thread, or an answer to one - the same call the Android post screen makes. */
    suspend fun send(target: Composing, subject: String, message: String) {
        isSending = true
        val nick = settings.nickName
        runCatching {
            when (target) {
                is Composing.NewThread ->
                    threadsRepo.sendThread(ZumpaThreadBody(nick, subject, message))

                is Composing.Reply -> threadsRepo.sendResponse(
                    threadId = target.threadId,
                    body = ZumpaThreadBody(nick, target.subject, message, target.threadId),
                )
            }
        }.onSuccess {
            status = "Sent"
            //the forum has something new on it either way, so what is on screen is stale
            reload()
            //and a reply is in the thread that is open, which reloads by being re-selected
            if (target is Composing.Reply) {
                selected = null
                selected = target.threadId
            }
        }.onFailure {
            status = it.message ?: "Could not send"
        }
        isSending = false
    }

    //Signed in and online is the whole condition for writing anything: the forum will not take a
    //post from anyone else, and an offline snapshot is a read of something that already happened.
    val canWrite = isLoggedIn && !isOffline

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
                                auth.logout()
                                status = "Signed out"
                                reload()
                            }
                        },
                        onToggleOffline = { prefs.isOffline = !isOffline },
                        onDownload = { scope.launch { download() } },
                    )
                },
            )

            //the list keeps its fab and a dialog behind it; only a thread gets a box that is
            //always up, because only a thread is something to be permanently answering
            val list: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize()) {
                    ThreadList(
                        state = listState,
                        threads = threads,
                        selectedId = selected,
                        hasMore = nextThreadId != null,
                        onSelect = { selected = it },
                        onEndReached = { scope.launch { appendNextPage() } },
                        onRetry = { scope.launch { reload() } },
                    )
                    if (canWrite) {
                        WriteFab(Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
                            isNewThreadOpen = true
                        }
                    }
                }
            }
            val reply: @Composable () -> Unit = {
                val id = selected
                if (canWrite && id != null) {
                    val target = Composing.Reply(id, subjectOf(threads, id))
                    ReplyPanel(target, isSending) { message ->
                        scope.launch { send(target, "", message) }
                    }
                }
            }

            if (isTwoPane) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(LIST_WEIGHT)) { list() }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(DividerColor))
                    Column(Modifier.weight(DETAIL_WEIGHT)) {
                        Box(Modifier.weight(1f)) { ThreadDetail(selected) }
                        reply()
                    }
                }
            } else if (isShowingDetail) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { ThreadDetail(selected) }
                    reply()
                }
            } else {
                list()
            }
        }
    }

    if (isNewThreadOpen && canWrite) {
        NewThreadDialog(
            isSending = isSending,
            onDismiss = { isNewThreadOpen = false },
            onSend = { subject, message ->
                scope.launch {
                    send(Composing.NewThread, subject, message)
                    isNewThreadOpen = false
                }
            },
        )
    }

    status?.let { StatusToast(it) { status = null } }

    if (isLoginOpen) {
        LoginDialog(
            onDismiss = { isLoginOpen = false },
            onSubmit = { user, password ->
                isLoginOpen = false
                scope.launch {
                    status = "Signing in..."
                    runCatching { auth.login(user, password) }
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

/**
 * The subject of a thread the list already knows about - a reply carries the subject it answers.
 */
private fun subjectOf(threads: List<ZumpaThread>, id: String): String =
    threads.firstOrNull { it.id == id }?.subject.orEmpty()
