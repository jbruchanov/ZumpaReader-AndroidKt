package com.scurab.android.zumpareader.ui.mainlist

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.scurab.android.zumpareader.ui.compose.QuickHideFab
import com.scurab.android.zumpareader.ui.compose.RevealRow
import com.scurab.android.zumpareader.ui.compose.RevealRowMenuButton
import com.scurab.android.zumpareader.ui.compose.quickHide
import com.scurab.android.zumpareader.ui.compose.rememberAnnotatedTextRenderer
import com.scurab.android.zumpareader.ui.compose.rememberQuickHideState
import com.scurab.android.zumpareader.ui.compose.zumpaRowBackground
import com.scurab.android.zumpareader.ui.compose.zumpaRowColor
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import org.koin.androidx.compose.koinViewModel
import com.scurab.android.zumpareader.util.formatThreadListTime

@Composable
fun MainListScreen(vm: MainListViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    //hoisted so the effect handler below can reach it, the way SubListScreen already takes one
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is MainListEffect.OpenThread -> navigator.openThread(effect.threadId)
                is MainListEffect.OpenSettings -> navigator.openSettings()
                is MainListEffect.OpenPostDialog -> navigator.openPostDialog()
                is MainListEffect.ShowOfflineDownloadDialog -> navigator.openOfflineDownload()
                is MainListEffect.ShareThread -> context.shareLink(effect.link)
                //not animated: the rows underneath have just been replaced, so there is nothing
                //meaningful to travel through - and from far down the list it would be a long trip
                is MainListEffect.ScrollToTop -> listState.scrollToItem(0)
                is ShowToast -> effect.text?.let { context.toast(it) } ?: context.toast(effect.resId)
                else -> Unit
            }
        }
    }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val eventHandler = vm
    MainListScreen(uiState, eventHandler, listState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainListScreen(
    uiState: MainListUiState,
    eventHandler: MainListEventHandler,
    listState: LazyListState = rememberLazyListState(),
) {
    //paging: the old adapter fired 15 rows from the end, so does this
    LaunchedEffect(listState, uiState.rows.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (uiState.rows.isNotEmpty() && last >= uiState.rows.size - LOAD_MORE_OFFSET) {
                    eventHandler.onEndReached()
                }
            }
    }

    val refreshState = rememberPullToRefreshState()
    val quickHideState = rememberQuickHideState()

    //enterAlways, as on a thread: the bar goes as the list is read downwards and comes back on the
    //first upward scroll, wherever in the list that is.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    /*
     * The height the bar has when it is fully out, which is what the list is padded by - not
     * `padding.calculateTopPadding()`, which is the bar's *current* height and so changes every
     * frame of a collapse. Feeding that to a LazyColumn's contentPadding would move the content
     * while it was already being scrolled, and it does not need to move: the rows scroll under this
     * bar by design, so a bar on its way out uncovers rows that were already there.
     */
    val expandedTopPadding = AppTheme.sizes.topBarHeight +
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()

    Scaffold(
        //the bar reads the list's scrolling through this
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppTheme.colorScheme.primaryBackground,
        //safeDrawing so the ime is in there too, and the content slot below is the only place that
        //applies any of it - anything else double counts
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { MainListTopBar(uiState, eventHandler, scrollBehavior) },
        floatingActionButton = {
            if (uiState.canInteract) {
                QuickHideFab(quickHideState) {
                    FloatingActionButton(
                        onClick = eventHandler::onFabClicked,
                        containerColor = AppTheme.colorScheme.context,
                        //white on the orange, which is what ic_add_white was
                        contentColor = AppTheme.colorScheme.primaryText,
                        //M3 draws a squircle, the old Material fab was round
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }
            }
        },
    ) { padding ->
        //the side insets stop here and travel down to the rows instead. Putting them on the
        //LazyColumn - as contentPadding or as a modifier - would inset the alternating background
        //with the text, and in landscape that leaves a bare stripe of window down one side.
        val layoutDirection = LocalLayoutDirection.current
        val rowPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            end = padding.calculateEndPadding(layoutDirection),
        )
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = eventHandler::onRefreshRequested,
            state = refreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = uiState.isLoading,
                    //the box is the whole screen now, so the spinner starts below the app bar
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = expandedTopPadding),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            //contentPadding, not padding: the rows scroll under the translucent app bar, and
            //the alternating background still runs edge to edge - which a side inset would break
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = expandedTopPadding,
                    bottom = padding.calculateBottomPadding(),
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .quickHide(quickHideState),
            ) {
                //the alternating background is keyed on list position, as the level-list was
                itemsIndexed(uiState.rows, key = { _, row -> row.id }) { index, row ->
                    ThreadRow(row, index, rowPadding, eventHandler)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainListTopBar(
    uiState: MainListUiState,
    eventHandler: MainListEventHandler,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)
    val offline = stringResource(R.string.offline)

    //the bar and its progress strip are one translucent pane, which the list slides under
    Box(Modifier.background(AppTheme.colorScheme.primaryBackground80p)) {
        TopAppBar(
            title = {
                Text(
                    text = if (uiState.isOffline) "$appName ($offline)" else appName,
                    style = AppTheme.typography.title,
                    color = AppTheme.colorScheme.primaryText,
                )
            },
            expandedHeight = AppTheme.sizes.topBarHeight,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                //and still transparent once scrolled. M3 fades in its own surface colour as a bar
                //collapses, which would put a second background over the translucent pane the Box
                //around this already draws - that pane is the background, black at 80%.
                scrolledContainerColor = Color.Transparent,
            ),
            scrollBehavior = scrollBehavior,
            //safeDrawing, not the systemBarsForVisualComponents the default uses: that
            //one leaves out the display cutout, which in landscape is exactly the inset on
            //the side the title runs into. Applied here and only here - the Box outside
            //keeps its background running edge to edge and under the status bar.
            windowInsets = WindowInsets.safeDrawing
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            actions = {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
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
        //A line between the header and what scrolls under it. Under the progress strip in the
        //stack on purpose: while a load is running that strip is the separator, and the two sitting
        //on top of each other would only thicken it.
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            thickness = AppTheme.sizes.divider,
            color = AppTheme.colorScheme.context,
        )
        if (uiState.isLoading) {
            //over the bar rather than under it, so switching it on cannot change the bar height -
            //that height is the list contentPadding, so it used to shift every row
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = AppTheme.colorScheme.context,
                trackColor = Color.Transparent,
                //M3 gaps the track, the old bar was a plain strip
                gapSize = 0.dp,
            )
        }
    }
}

@Composable
private fun ThreadRow(
    row: ThreadRowUiState,
    index: Int,
    contentPadding: PaddingValues,
    eventHandler: MainListEventHandler,
) {
    val renderer = rememberAnnotatedTextRenderer()
    val subject = remember(row.subject, renderer) { renderer.subject(row.subject) }
    val time = remember(row.time, row.useShortTimeFormat) {
        row.time.formatThreadListTime(row.useShortTimeFormat)
    }

    RevealRow(
        isOpen = row.isMenuOpen,
        background = zumpaRowColor(index),
        modifier = Modifier.fillMaxWidth(),
        menuStartPadding = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
        menu = { ThreadRowMenu(row, eventHandler) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                //so the state bar has something for its fillMaxHeight to measure against
                .height(IntrinsicSize.Min)
                .zumpaRowBackground(index, row.isSelected)
                .combinedClickable(
                    interactionSource = null,
                    indication = ripple(),
                    onClick = { eventHandler.onThreadClicked(row.id) },
                    onLongClick = { eventHandler.onThreadLongPressed(row.id) },
                ),
        ) {
            //hard against the edge, as the xml had it - the row colour, the ripple and this line
            //all still reach the window, and the inset opens up after it rather than before
            ThreadStateBar(row.state)
            Column(
                modifier = Modifier
                    .weight(1f)
                    //the side insets sit between the state line and the text: the line keeps the
                    //edge, the reading matter clears a navigation bar or a cutout
                    .padding(contentPadding)
                    .padding(AppTheme.spaces.listItemPadding),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subject.text,
                        inlineContent = subject.inlineContent,
                        style = AppTheme.typography.subject,
                        color = AppTheme.colorScheme.subject,
                        maxLines = SUBJECT_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppTheme.sizes.subjectMinHeight),
                    )
                    if (row.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = AppTheme.colorScheme.context,
                            modifier = Modifier.padding(start = AppTheme.spaces.tiny),
                        )
                    }
                }
                //author takes the slack, so the count and the time sit against the right edge
                Row(
                    modifier = Modifier.padding(top = AppTheme.spaces.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.author,
                        style = AppTheme.typography.author,
                        color = AppTheme.colorScheme.author,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    row.lastAuthor?.let {
                        Text(
                            text = it,
                            style = AppTheme.typography.nickName,
                            color = AppTheme.colorScheme.nickName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = AppTheme.spaces.normal),
                        )
                    }
                    Text(
                        text = row.answerCount.toString(),
                        style = AppTheme.typography.threads,
                        color = AppTheme.colorScheme.threads,
                        modifier = Modifier.padding(horizontal = AppTheme.spaces.normal),
                    )
                    Text(
                        text = time,
                        style = AppTheme.typography.date,
                        color = AppTheme.colorScheme.date,
                    )
                }
            }
        }
    }
}

/** The `LevelListDrawable` down the left of a row, as a coloured bar. */
@Composable
private fun ThreadStateBar(state: ThreadState) {
    val color = when (state) {
        //no bar at all for a read thread, as level 0 of the state drawable was transparent
        ThreadState.None -> Color.Transparent
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
    RevealRowMenuButton(rememberVectorPainter(Icons.Filled.Star)) {
        eventHandler.onFavoriteClicked(row.id)
    }
    RevealRowMenuButton(rememberVectorPainter(Icons.Filled.Block)) {
        eventHandler.onIgnoreClicked(row.id)
    }
    RevealRowMenuButton(rememberVectorPainter(Icons.Filled.Share)) {
        eventHandler.onShareClicked(row.id)
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
            ThreadRow(Fixtures.MainList.row(state = state), state.ordinal, PaddingValues(), mock())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ThreadRowMenuOpenPreview() = AppTheme {
    ThreadRow(Fixtures.MainList.row(isMenuOpen = true), 0, PaddingValues(), mock())
}
