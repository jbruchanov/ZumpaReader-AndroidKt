package com.scurab.android.zumpareader.ui.mainlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.test.Fixtures
import com.scurab.android.zumpareader.test.empty
import com.scurab.android.zumpareader.test.loading
import com.scurab.android.zumpareader.test.mock
import com.scurab.android.zumpareader.test.offline
import com.scurab.android.zumpareader.test.row
import com.scurab.android.zumpareader.test.uiState
import android.content.Context
import android.content.Intent
import com.scurab.android.zumpareader.ui.compose.LocalNavigator
import com.scurab.android.zumpareader.ui.compose.rememberAnnotatedTextRenderer
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainListScreen(vm: MainListViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is MainListEffect.OpenThread -> navigator.openThread(effect.threadId)
                is MainListEffect.OpenSettings -> navigator.openSettings()
                is MainListEffect.OpenPostDialog -> navigator.openPostDialog()
                is MainListEffect.ShowOfflineDownloadDialog -> navigator.openOfflineDownload()
                is MainListEffect.ShareThread -> context.shareLink(effect.link)
                is ShowToast -> effect.text?.let { context.toast(it) } ?: context.toast(effect.resId)
                else -> Unit
            }
        }
    }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val eventHandler = vm
    MainListScreen(uiState, eventHandler)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainListScreen(uiState: MainListUiState, eventHandler: MainListEventHandler) {
    val listState = rememberLazyListState()

    //paging: the old adapter fired 15 rows from the end, so does this
    LaunchedEffect(listState, uiState.rows.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (uiState.rows.isNotEmpty() && last >= uiState.rows.size - LOAD_MORE_OFFSET) {
                    eventHandler.onEndReached()
                }
            }
    }

    Scaffold(
        containerColor = AppTheme.colorScheme.primaryBackground,
        topBar = { MainListTopBar(uiState, eventHandler) },
        floatingActionButton = {
            if (uiState.canInteract) {
                FloatingActionButton(
                    onClick = eventHandler::onFabClicked,
                    containerColor = AppTheme.colorScheme.context,
                    contentColor = AppTheme.colorScheme.primaryBackground,
                ) {
                    Icon(painterResource(R.drawable.ic_add_black), contentDescription = null)
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = eventHandler::onRefreshRequested,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(uiState.rows, key = { it.id }) { row ->
                    ThreadRow(row, eventHandler)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainListTopBar(uiState: MainListUiState, eventHandler: MainListEventHandler) {
    var menuExpanded by remember { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)
    val offline = stringResource(R.string.offline)

    Column {
        TopAppBar(
            title = {
                Text(
                    text = if (uiState.isOffline) "$appName ($offline)" else appName,
                    style = AppTheme.typography.subject,
                    color = AppTheme.colorScheme.primaryText,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AppTheme.colorScheme.primaryBackground,
            ),
            actions = {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert_black),
                        contentDescription = null,
                        tint = AppTheme.colorScheme.context,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings)) },
                        onClick = {
                            menuExpanded = false
                            eventHandler.onSettingsClicked()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (uiState.isOffline) R.string.online else R.string.offline))
                        },
                        onClick = {
                            menuExpanded = false
                            eventHandler.onOfflineToggled()
                        },
                    )
                }
            },
        )
        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AppTheme.colorScheme.context,
                trackColor = AppTheme.colorScheme.primaryBackground,
            )
        }
    }
}

@Composable
private fun ThreadRow(row: ThreadRowUiState, eventHandler: MainListEventHandler) {
    val renderer = rememberAnnotatedTextRenderer()
    val subject = remember(row.subject, renderer) { renderer.subject(row.subject) }
    val time = remember(row.time, row.useShortTimeFormat) {
        (if (row.useShortTimeFormat) shortDateFormat else dateFormat).format(Date(row.time))
    }

    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                //so the state bar's fillMaxHeight has something to measure against
                .height(IntrinsicSize.Min)
                .background(
                    if (row.isSelected) {
                        AppTheme.colorScheme.selectedBackground
                    } else {
                        AppTheme.colorScheme.primaryBackground
                    }
                )
                .combinedClickable(
                    onClick = { eventHandler.onThreadClicked(row.id) },
                    onLongClick = { eventHandler.onThreadLongPressed(row.id) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            //outside the padding, hard against the edge, as the xml had it
            ThreadStateBar(row.state)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(AppTheme.spaces.listItemPadding)
                    .padding(start = AppTheme.spaces.normal),
            ) {
                Text(
                    text = subject.text,
                    inlineContent = subject.inlineContent,
                    style = AppTheme.typography.subject,
                    color = AppTheme.colorScheme.subject,
                    maxLines = SUBJECT_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spaces.small)) {
                    Text(
                        text = row.author,
                        style = AppTheme.typography.author,
                        color = AppTheme.colorScheme.author,
                    )
                    row.lastAuthor?.let {
                        Text(
                            text = it,
                            style = AppTheme.typography.nickName,
                            color = AppTheme.colorScheme.nickName,
                        )
                    }
                    Text(
                        text = time,
                        style = AppTheme.typography.date,
                        color = AppTheme.colorScheme.date,
                    )
                }
            }
            if (row.isFavorite) {
                Icon(
                    painter = painterResource(R.drawable.ic_grade_black),
                    contentDescription = null,
                    tint = AppTheme.colorScheme.context,
                )
            }
            Text(
                text = row.answerCount.toString(),
                style = AppTheme.typography.threads,
                color = AppTheme.colorScheme.threads,
                modifier = Modifier.padding(horizontal = AppTheme.spaces.large),
            )
        }

        AnimatedVisibility(
            visible = row.isMenuOpen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            ThreadRowMenu(row, eventHandler)
        }
    }
}

/** The `LevelListDrawable` down the left of a row, as a coloured bar. */
@Composable
private fun ThreadStateBar(state: ThreadState) {
    val color = when (state) {
        ThreadState.None -> AppTheme.colorScheme.primaryBackground
        ThreadState.New -> AppTheme.colorScheme.threadStateNew
        ThreadState.Updated -> AppTheme.colorScheme.threadStateUpdated
        ThreadState.Own -> AppTheme.colorScheme.threadStateOwn
        ThreadState.ResponseForYou -> AppTheme.colorScheme.threadStateResponseForYou
    }
    Box(
        Modifier
            .width(AppTheme.sizes.threadStateBarWidth)
            .fillMaxHeight()
            .background(color)
    )
}

@Composable
private fun ThreadRowMenu(row: ThreadRowUiState, eventHandler: MainListEventHandler) {
    Row(
        modifier = Modifier.background(AppTheme.colorScheme.secondaryBackground),
    ) {
        IconButton(onClick = { eventHandler.onFavoriteClicked(row.id) }) {
            Icon(
                painterResource(R.drawable.ic_grade_black_36dp),
                contentDescription = null,
                tint = AppTheme.colorScheme.context,
            )
        }
        IconButton(onClick = { eventHandler.onIgnoreClicked(row.id) }) {
            Icon(
                painterResource(R.drawable.ic_block_black_36dp),
                contentDescription = null,
                tint = AppTheme.colorScheme.context,
            )
        }
        IconButton(onClick = { eventHandler.onShareClicked(row.id) }) {
            Icon(
                painterResource(R.drawable.ic_share_black),
                contentDescription = null,
                tint = AppTheme.colorScheme.context,
            )
        }
    }
}

private fun Context.shareLink(link: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    } catch (e: Exception) {
        toast(R.string.unable_to_finish_operation)
    }
}

private val dateFormat = SimpleDateFormat("dd.MM. HH:mm.ss", Locale.US)
private val shortDateFormat = SimpleDateFormat("HH:mm", Locale.US)
private const val LOAD_MORE_OFFSET = 15
private const val SUBJECT_MAX_LINES = 3

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 420)
@Composable
private fun MainListScreenPreview() = AppTheme {
    MainListScreen(Fixtures.MainList.uiState(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 200)
@Composable
private fun MainListScreenEmptyPreview() = AppTheme {
    MainListScreen(Fixtures.MainList.empty(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 200)
@Composable
private fun MainListScreenOfflinePreview() = AppTheme {
    MainListScreen(Fixtures.MainList.offline(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 200)
@Composable
private fun MainListScreenLoadingPreview() = AppTheme {
    MainListScreen(Fixtures.MainList.loading(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ThreadRowStatesPreview() = AppTheme {
    Column {
        ThreadState.entries.forEach { state ->
            ThreadRow(Fixtures.MainList.row(state = state), mock())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ThreadRowMenuOpenPreview() = AppTheme {
    ThreadRow(Fixtures.MainList.row(isMenuOpen = true), mock())
}
