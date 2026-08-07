package com.scurab.android.zumpareader.ui.sublist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.CopyToClipboard
import com.scurab.android.zumpareader.arch.HideKeyboard
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.test.Fixtures
import com.scurab.android.zumpareader.test.message
import com.scurab.android.zumpareader.test.mock
import com.scurab.android.zumpareader.test.survey
import com.scurab.android.zumpareader.test.replying
import com.scurab.android.zumpareader.test.sending
import com.scurab.android.zumpareader.test.uiState
import com.scurab.android.zumpareader.test.withSurvey
import com.scurab.android.zumpareader.ui.compose.LocalNavigator
import com.scurab.android.zumpareader.ui.compose.rememberAnnotatedTextRenderer
import com.scurab.android.zumpareader.ui.compose.zumpaRowBackground
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import com.scurab.android.zumpareader.util.saveToClipboard
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SubListScreen(threadId: String, vm: SubListViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is SubListEffect.ScrollToBottom ->
                    listState.animateScrollToItem(maxOf(0, listState.layoutInfo.totalItemsCount - 1))

                is SubListEffect.ScrollToTop -> listState.animateScrollToItem(0)
                is SubListEffect.OpenThread -> navigator.openThread(effect.threadId)
                is SubListEffect.OpenImage -> navigator.openImage(effect.url)
                is SubListEffect.OpenLink -> navigator.openLink(effect.url)
                is SubListEffect.OpenPostDialog -> navigator.openPostDialog()
                is CopyToClipboard -> {
                    context.saveToClipboard(effect.text.toString())
                    context.toast(R.string.saved_into_clipboard)
                }

                is HideKeyboard -> keyboard?.hide()
                is ShowToast -> effect.text?.let { context.toast(it) } ?: context.toast(effect.resId)
                else -> Unit
            }
        }
    }
    LaunchedEffect(threadId) { vm.start(threadId) }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val eventHandler = vm
    SubListScreen(uiState, eventHandler, listState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubListScreen(
    uiState: SubListUiState,
    eventHandler: SubListEventHandler,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    //back closes the reply panel before it leaves the screen, as it always did
    BackHandler(enabled = uiState.isPostPanelVisible) { eventHandler.onPostPanelDismissed() }

    val renderer = rememberAnnotatedTextRenderer()
    val title = remember(uiState.title, renderer) {
        if (uiState.title.isEmpty()) null else renderer.title(uiState.title)
    }

    Scaffold(
        containerColor = AppTheme.colorScheme.primaryBackground,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = title?.text ?: androidx.compose.ui.text.AnnotatedString(
                                stringResource(R.string.app_name)
                            ),
                            inlineContent = title?.inlineContent.orEmpty(),
                            style = AppTheme.typography.subject,
                            color = AppTheme.colorScheme.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppTheme.colorScheme.primaryBackground,
                    ),
                )
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AppTheme.colorScheme.context,
                        trackColor = AppTheme.colorScheme.primaryBackground,
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.canPost && !uiState.isPostPanelVisible) {
                FloatingActionButton(
                    onClick = eventHandler::onPostPanelRequested,
                    containerColor = AppTheme.colorScheme.context,
                    contentColor = AppTheme.colorScheme.primaryBackground,
                ) {
                    Icon(painterResource(R.drawable.ic_pen_black), contentDescription = null)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = eventHandler::onRefreshRequested,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .bottomPullToRefresh(
                            listState = listState,
                            enabled = !uiState.isLoading,
                            onTriggered = eventHandler::onRefreshRequested,
                        ),
                ) {
                    items(uiState.rows, key = { it.key() }, contentType = { it::class }) { row ->
                        SubListRow(row, eventHandler)
                    }
                }
            }
            if (uiState.isPostPanelVisible) {
                ReplyPanel(uiState, eventHandler)
            }
        }
    }
}

private fun SubListRowUiState.key(): String = when (this) {
    is SubListRowUiState.Message -> "m$itemIndex"
    is SubListRowUiState.Link -> "l$itemIndex$url"
    is SubListRowUiState.Image -> "i$itemIndex$url"
    is SubListRowUiState.Survey -> "s$itemIndex"
}

@Composable
private fun SubListRow(row: SubListRowUiState, eventHandler: SubListEventHandler) {
    when (row) {
        is SubListRowUiState.Message -> MessageRow(row, eventHandler)
        is SubListRowUiState.Link -> LinkRow(row, eventHandler)
        is SubListRowUiState.Image -> ImageRow(row, eventHandler)
        is SubListRowUiState.Survey -> SurveyCard(row.survey, eventHandler)
    }
}

@Composable
private fun MessageRow(row: SubListRowUiState.Message, eventHandler: SubListEventHandler) {
    val interactionSource = remember { MutableInteractionSource() }
    val renderer = rememberAnnotatedTextRenderer()
    val body = remember(row.body, renderer) { renderer.body(row.body) }
    val author = remember(row.author, row.rating, renderer) { renderer.author(row.author, row.rating) }
    val time = remember(row.time) { dateFormat.format(Date(row.time)) }

    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zumpaRowBackground(row.itemIndex, interactionSource = interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { eventHandler.onMessageClicked(row) },
                    onLongClick = { eventHandler.onMessageLongPressed(row) },
                )
                .padding(AppTheme.spaces.listItemPadding),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spaces.small)) {
                Text(
                    text = author.text,
                    style = AppTheme.typography.author,
                    color = AppTheme.colorScheme.author,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = time,
                    style = AppTheme.typography.date,
                    color = AppTheme.colorScheme.date,
                )
            }
            Text(
                text = body.text,
                inlineContent = body.inlineContent,
                style = AppTheme.typography.message,
                color = AppTheme.colorScheme.subject,
            )
        }

        AnimatedVisibility(
            visible = row.isMenuOpen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            MessageRowMenu(row, eventHandler)
        }
    }
}

@Composable
private fun MessageRowMenu(row: SubListRowUiState.Message, eventHandler: SubListEventHandler) {
    Row(Modifier.background(AppTheme.colorScheme.secondaryBackground)) {
        IconButton(onClick = { eventHandler.onReplyClicked(row.authorReal) }) {
            Icon(
                painterResource(R.drawable.ic_reply_black),
                contentDescription = null,
                tint = AppTheme.colorScheme.context,
            )
        }
        IconButton(onClick = { eventHandler.onCopyClicked(row.body) }) {
            Icon(
                painterResource(R.drawable.ic_copy_black),
                contentDescription = null,
                tint = AppTheme.colorScheme.context,
            )
        }
        IconButton(onClick = { eventHandler.onQuoteClicked(row.author, row.body) }) {
            Icon(
                painterResource(R.drawable.ic_speak_black),
                contentDescription = null,
                tint = AppTheme.colorScheme.context,
            )
        }
    }
}

@Composable
private fun LinkRow(row: SubListRowUiState.Link, eventHandler: SubListEventHandler) {
    TextButton(
        onClick = { eventHandler.onLinkClicked(row.url) },
        modifier = Modifier
            .fillMaxWidth()
            .zumpaRowBackground(row.itemIndex)
            .padding(horizontal = AppTheme.spaces.listItemPadding),
    ) {
        Text(
            text = row.url,
            style = AppTheme.typography.button,
            color = AppTheme.colorScheme.buttonText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ImageRow(row: SubListRowUiState.Image, eventHandler: SubListEventHandler) {
    AsyncImage(
        model = row.url,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .zumpaRowBackground(row.itemIndex)
            .combinedClickable(
                onClick = { eventHandler.onImageClicked(row.url) },
                onLongClick = { eventHandler.onLinkClicked(row.url) },
            )
            .padding(AppTheme.spaces.listItemPadding),
    )
}

@Composable
private fun SurveyCard(survey: SurveyUiState, eventHandler: SubListEventHandler) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colorScheme.secondaryBackground)
            .padding(AppTheme.spaces.listItemPadding),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spaces.small),
    ) {
        Text(
            text = "${survey.question}\n${survey.responses}",
            style = AppTheme.typography.subject,
            color = AppTheme.colorScheme.primaryText,
        )
        survey.items.forEach { item ->
            SurveyOption(item, eventHandler)
        }
    }
}

@Composable
private fun SurveyOption(item: SurveyItemUiState, eventHandler: SubListEventHandler) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.colorScheme.primaryBackground, AppTheme.shapes.button)
    ) {
        //the filled portion is the vote share, as the level drawable used to be
        Box(
            Modifier
                .fillMaxWidth(item.percents / 100f)
                .matchParentSize()
                .background(
                    if (item.voted) AppTheme.colorScheme.context50p else AppTheme.colorScheme.context25p,
                    AppTheme.shapes.button,
                )
        )
        TextButton(
            onClick = { eventHandler.onSurveyItemClicked(item) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "${item.text} (${item.percents}%)",
                style = AppTheme.typography.surveyButton,
                color = AppTheme.colorScheme.buttonText,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReplyPanel(uiState: SubListUiState, eventHandler: SubListEventHandler) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colorScheme.secondaryBackground)
            .padding(AppTheme.spaces.tiny),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = uiState.draft.text,
            onValueChange = eventHandler::onDraftChanged,
            enabled = !uiState.isSending,
            textStyle = AppTheme.typography.message,
            shape = AppTheme.shapes.editText,
            maxLines = REPLY_MAX_LINES,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = eventHandler::onSendClicked,
            enabled = !uiState.isSending && !uiState.draft.isBlank,
        ) {
            Icon(
                painterResource(R.drawable.ic_send_black),
                contentDescription = null,
                tint = if (uiState.draft.isBlank) {
                    AppTheme.colorScheme.contextTextDisabled
                } else {
                    AppTheme.colorScheme.context
                },
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("HH:mm.ss", Locale.US)
private const val REPLY_MAX_LINES = 6

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 500)
@Composable
private fun SubListScreenPreview() = AppTheme {
    SubListScreen(Fixtures.SubList.uiState(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 500)
@Composable
private fun SubListScreenWithSurveyPreview() = AppTheme {
    SubListScreen(Fixtures.SubList.withSurvey(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 500)
@Composable
private fun SubListScreenReplyingPreview() = AppTheme {
    SubListScreen(Fixtures.SubList.replying(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 500)
@Composable
private fun SubListScreenSendingPreview() = AppTheme {
    SubListScreen(Fixtures.SubList.sending(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MessageRowMenuOpenPreview() = AppTheme {
    MessageRow(Fixtures.SubList.message(isMenuOpen = true), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SurveyCardPreview() = AppTheme {
    SurveyCard(Fixtures.SubList.survey(), mock())
}
