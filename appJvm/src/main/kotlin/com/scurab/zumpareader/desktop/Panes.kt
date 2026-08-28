package com.scurab.zumpareader.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scurab.android.zumpareader.model.ThreadState
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.model.ZumpaThreadItem
import com.scurab.android.zumpareader.reader.Smileys
import com.scurab.android.zumpareader.repository.ZumpaThreadRepository
import com.scurab.android.zumpareader.util.formatPostTime
import com.scurab.android.zumpareader.util.formatThreadListTime
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * The list pane.
 *
 * Rows are clickable, which they were not: `ThreadRow` had no click handler at all, so there was
 * nothing for a pointer to do with one and no second pane for it to open.
 *
 * @param states the new/updated/own decoration per thread id, which is what the coloured bar down
 * the left of a row is. Handed in rather than worked out here: it depends on how much of each thread
 * has been read, which is a repository's business - see `App`.
 */
@Composable
internal fun ThreadList(
    state: Loadable,
    threads: List<ZumpaThread>,
    states: Map<String, ThreadState>,
    selectedId: String?,
    hasMore: Boolean,
    onSelect: (String) -> Unit,
    onEndReached: () -> Unit,
    onRetry: () -> Unit,
) {
    if (state is Loadable.Failed && threads.isEmpty()) {
        Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Could not load: ${state.message}", color = Error)
                TextButton(onClick = onRetry) { Text("Retry", color = Accent) }
            }
        }
        return
    }
    if (threads.isEmpty()) {
        Centered {
            if (state is Loadable.Loading) {
                CircularProgressIndicator(color = Accent)
            } else {
                Text("Nothing here", color = Muted)
            }
        }
        return
    }

    val listState = rememberLazyListState()

    //the same trigger the Android list uses - a page is asked for a screenful before the end,
    //not at it, so the rows are already there by the time the reader arrives
    LaunchedEffect(listState, threads.size, hasMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (hasMore && last >= threads.size - LOAD_MORE_OFFSET) onEndReached()
            }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(threads, key = { _, thread -> thread.id }) { index, thread ->
            ThreadRow(
                thread = thread,
                state = states[thread.id] ?: ThreadState.New,
                isEven = index % 2 == 0,
                isSelected = thread.id == selectedId,
                onClick = { onSelect(thread.id) },
            )
        }
        if (hasMore) {
            item(key = "next-page") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(Spaces.large),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Sizes.progressBar),
                        color = Accent,
                        strokeWidth = Sizes.divider * 2,
                    )
                }
            }
        }
    }
}

/**
 * One thread, laid out as the phone lays it out: the state bar hard against the left edge, then the
 * subject, then the author, the last author, the answer count and the time on one line.
 *
 * The subject goes through [Smileys] - the same table `:appAndroid`'s renderer uses - so `:)` in a
 * subject is a face here too. That is only possible now that a smiley is a character rather than a
 * drawable this module cannot reach.
 */
@Composable
private fun ThreadRow(
    thread: ZumpaThread,
    state: ThreadState,
    isEven: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val subject = remember(thread.subject) { Smileys.replaceIn(thread.subject) }
    val lastAuthor = thread.lastAuthor
    val time = remember(thread.time, lastAuthor) {
        //only the time when a last author is present - as on the phone, there is no room for both
        thread.time.formatThreadListTime(useShortFormat = lastAuthor != null)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            //so the state bar has something for its fillMaxHeight to measure against
            .height(IntrinsicSize.Min)
            .background(if (isEven) RowEven else RowOdd)
            .then(if (isSelected) Modifier.background(SelectedRow) else Modifier)
            .clickable(onClick = onClick),
    ) {
        //before the padding, so the line keeps the window edge and the text clears it
        ThreadStateBar(state)
        Column(Modifier.weight(1f).padding(Spaces.listItemPadding)) {
            Text(
                text = subject,
                color = Content,
                fontSize = FontSizes.subject,
                maxLines = SUBJECT_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = Sizes.subjectMinHeight),
            )
            //the author takes the slack, so the count and the time sit against the right edge
            Row(
                modifier = Modifier.padding(top = Spaces.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = thread.author,
                    color = Accent,
                    fontSize = FontSizes.author,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                lastAuthor?.let {
                    Text(
                        text = it,
                        color = Accent,
                        fontSize = FontSizes.nickName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Spaces.normal),
                    )
                }
                Text(
                    text = thread.items.toString(),
                    color = Content,
                    fontSize = ThreadCountSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spaces.normal),
                )
                Text(text = time, color = Content, fontSize = FontSizes.date)
            }
        }
    }
}

/**
 * The `LevelListDrawable` down the left of a row, as a coloured bar - the same four colours the
 * phone uses, off the same [ThreadState].
 *
 * A read thread gets no bar at all, because level 0 of that drawable was transparent.
 */
@Composable
private fun ThreadStateBar(state: ThreadState) {
    val color = when (state) {
        ThreadState.None -> Color.Transparent
        ThreadState.New -> StateNew
        ThreadState.Updated -> StateUpdated
        ThreadState.Own -> StateOwn
        ThreadState.ResponseForYou -> StateResponseForYou
    }
    Box(Modifier.width(Sizes.threadStateBar).fillMaxHeight().background(color))
}

/** The detail pane: whatever the list has selected, or an invitation to select something. */
@Composable
internal fun ThreadDetail(threadId: String?, reloadToken: Int = 0) {
    if (threadId == null) {
        Centered { Text("Pick a thread", color = Muted) }
        return
    }

    val threads = koinInject<ZumpaThreadRepository>()
    val listState = rememberLazyListState()
    var state by remember(threadId) { mutableStateOf<Loadable>(Loadable.Loading) }
    var items by remember(threadId) { mutableStateOf<List<ZumpaThreadItem>>(emptyList()) }

    //[reloadToken] as well as the id: a reply lands in the thread already on screen, so the id has
    //not changed and there would be nothing for this to react to. Bumping a counter is also why the
    //caller no longer sets `selected` to null and back - two writes to one state in a single
    //coroutine are coalesced into one snapshot, so that never registered as a change at all.
    LaunchedEffect(threadId, reloadToken) {
        state = Loadable.Loading
        runCatching { threads.loadThread(threadId) }
            .onSuccess {
                items = it
                state = Loadable.Loaded
            }
            .onFailure {
                state = Loadable.Failed(it.message ?: it::class.simpleName ?: "failed")
            }
    }

    //a reload the reader asked for by writing something lands on what they wrote; opening a thread
    //cold does not, which matches the phone - it scrolls to the top when switching threads
    LaunchedEffect(items, reloadToken) {
        if (reloadToken > 0 && items.isNotEmpty()) {
            listState.animateScrollToItem(items.lastIndex)
        }
    }

    when (val current = state) {
        is Loadable.Loading -> Centered { CircularProgressIndicator(color = Accent) }
        is Loadable.Failed -> Centered {
            Text("Could not load: ${current.message}", color = Error)
        }

        is Loadable.Loaded -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(items) { index, item -> PostRow(item, isEven = index % 2 == 0) }
        }
    }
}

/**
 * One message, laid out as the phone lays it out: author and time on a line, then the body at the
 * subject size - which is what that `TextView` always was, not the 13sp of the post editor.
 */
@Composable
private fun PostRow(item: ZumpaThreadItem, isEven: Boolean) {
    val body = remember(item.body) { Smileys.replaceIn(item.body) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isEven) RowEven else RowOdd)
            .padding(Spaces.listItemPadding),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spaces.small)) {
            Text(
                text = item.author,
                color = Accent,
                fontSize = FontSizes.author,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(item.time.formatPostTime(), color = Content, fontSize = FontSizes.date)
        }
        Text(
            text = body,
            color = Content,
            fontSize = FontSizes.subject,
            modifier = Modifier.padding(top = Spaces.small),
        )
        //the pictures and links the shared parser pulled out of the body
        PostUrls(item.urls.orEmpty())
    }
}

/**
 * The menu the bar had none of. Reload was its only action, as a button, so there was no way in to
 * signing in or to offline mode at all.
 */
@Composable
internal fun OverflowMenu(
    isOffline: Boolean,
    isLoggedIn: Boolean,
    onReload: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onToggleOffline: () -> Unit,
    onDownload: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    //a label rather than an icon: the material icon artifacts are an Android-app dependency this
    //module does not carry, and one glyph is not worth adding them for
    TextButton(onClick = { expanded = true }) { Text("Menu", color = Accent) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        MenuItem("Reload") { expanded = false; onReload() }
        if (isLoggedIn) {
            MenuItem("Sign out") { expanded = false; onLogout() }
        } else {
            MenuItem("Sign in...") { expanded = false; onLogin() }
        }
        MenuItem(if (isOffline) "Go online" else "Go offline") {
            expanded = false
            onToggleOffline()
        }
        //offline mode with no snapshot is an empty list, so the way to make one sits next to it
        MenuItem("Download offline data") { expanded = false; onDownload() }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick)
}

@Composable
internal fun LoginDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("User") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(user, password) },
                enabled = user.isNotBlank() && password.isNotBlank(),
            ) { Text("Sign in") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Whatever just happened, said once and then gone - the desktop stand-in for a toast. */
@Composable
internal fun StatusToast(message: String, onDone: () -> Unit) {
    LaunchedEffect(message) {
        delay(STATUS_MILLIS)
        onDone()
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = message,
            color = Content,
            fontSize = 13.sp,
            modifier = Modifier.background(RowOdd).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** The Android list fires its next page 15 rows from the end; so does this one. */
private const val LOAD_MORE_OFFSET = 15

private const val SUBJECT_MAX_LINES = 3

private const val STATUS_MILLIS = 3_000L
