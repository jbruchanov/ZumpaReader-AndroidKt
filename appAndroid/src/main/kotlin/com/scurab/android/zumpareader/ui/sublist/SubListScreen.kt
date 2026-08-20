package com.scurab.android.zumpareader.ui.sublist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SpeakerNotes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Create
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
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
import com.scurab.android.zumpareader.ui.compose.QuickHideFab
import com.scurab.android.zumpareader.ui.compose.RevealRow
import com.scurab.android.zumpareader.ui.compose.RevealRowMenuButton
import com.scurab.android.zumpareader.ui.compose.quickHide
import com.scurab.android.zumpareader.ui.compose.rememberAnnotatedTextRenderer
import com.scurab.android.zumpareader.ui.compose.rememberQuickHideState
import com.scurab.android.zumpareader.ui.compose.shimmer
import com.scurab.android.zumpareader.ui.compose.zumpaRowBackground
import com.scurab.android.zumpareader.ui.compose.zumpaRowColor
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import com.scurab.android.zumpareader.util.saveToClipboard
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import com.scurab.android.zumpareader.util.formatPostTime

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

    val quickHideState = rememberQuickHideState()
    val renderer = rememberAnnotatedTextRenderer()
    val title = remember(uiState.title, renderer) {
        if (uiState.title.isEmpty()) null else renderer.title(uiState.title)
    }

    Scaffold(
        containerColor = AppTheme.colorScheme.primaryBackground,
        //safeDrawing so the ime is in there too, and the content slot below is the only place that
        //applies any of it - anything else double counts
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            //the bar and its progress strip are one translucent pane, which the list slides under
            Box(Modifier.background(AppTheme.colorScheme.primaryBackground80p)) {
                TopAppBar(
                    title = {
                        Text(
                            text = title?.text ?: androidx.compose.ui.text.AnnotatedString(
                                stringResource(R.string.app_name)
                            ),
                            inlineContent = title?.inlineContent.orEmpty(),
                            style = AppTheme.typography.title,
                            color = AppTheme.colorScheme.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    expandedHeight = AppTheme.sizes.topBarHeight,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
                if (uiState.isLoading) {
                    //over the bar rather than under it, so switching it on cannot change the bar
                    //height - that height is the list contentPadding, so it used to shift every row
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
        },
        floatingActionButton = {
            if (uiState.canPost && !uiState.isPostPanelVisible) {
                QuickHideFab(quickHideState) {
                    FloatingActionButton(
                        onClick = eventHandler::onPostPanelRequested,
                        containerColor = AppTheme.colorScheme.context,
                        contentColor = AppTheme.colorScheme.primaryBackground,
                        //M3 draws a squircle, the old Material fab was round
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Filled.Create, contentDescription = null)
                    }
                }
            }
        },
    ) { padding ->
        //one inset, applied once: whatever is at the bottom of the screen takes it - the reply panel
        //when it is up, otherwise the list. Adding imePadding on top of this is what counted twice.
        val bottomInset = padding.calculateBottomPadding()

        Column(Modifier.fillMaxSize()) {
            //both ends refresh, so the spinner shows up at whichever end the drag came from
            var pulledFromBottom by remember { mutableStateOf(false) }
            val topState = rememberPullToRefreshState()
            val bottomState = rememberPullToRefreshState()
            val refreshingFromTop = uiState.isLoading && !pulledFromBottom
            val refreshingFromBottom = uiState.isLoading && pulledFromBottom

            //PullToRefreshBox would do the top half, but it has no way to disarm its own gesture
            //while the *bottom* one is reloading - its isRefreshing is both the gate and the
            //indicator, and here the two have to say different things
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pullToRefresh(
                        isRefreshing = refreshingFromTop,
                        state = topState,
                        enabled = !uiState.isLoading,
                        onRefresh = {
                            pulledFromBottom = false
                            eventHandler.onRefreshRequested()
                        },
                    ),
            ) {
                LazyColumn(
                    state = listState,
                    //contentPadding, not padding: the rows scroll under the translucent app bar
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = if (uiState.isPostPanelVisible) 0.dp else bottomInset,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .quickHide(quickHideState)
                        .bottomPullToRefresh(
                            listState = listState,
                            state = bottomState,
                            isRefreshing = refreshingFromBottom,
                            enabled = !uiState.isLoading,
                            onTriggered = {
                                pulledFromBottom = true
                                eventHandler.onRefreshRequested()
                            },
                        ),
                ) {
                    items(uiState.rows, key = { it.key() }, contentType = { it::class }) { row ->
                        SubListRow(row, eventHandler)
                    }
                }
                PullToRefreshDefaults.Indicator(
                    state = topState,
                    isRefreshing = refreshingFromTop,
                    //the box reaches under the app bar now, so the spinner starts below it
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = padding.calculateTopPadding()),
                )
                BottomPullToRefreshIndicator(
                    state = bottomState,
                    isRefreshing = refreshingFromBottom,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (uiState.isPostPanelVisible) 0.dp else bottomInset),
                )
            }
            if (uiState.isPostPanelVisible) {
                ReplyPanel(uiState, eventHandler, bottomInset)
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
    val renderer = rememberAnnotatedTextRenderer()
    val body = remember(row.body, renderer) { renderer.body(row.body) }
    val author = remember(row.author, row.rating, renderer) { renderer.author(row.author, row.rating) }
    val time = remember(row.time) { row.time.formatPostTime() }

    RevealRow(
        isOpen = row.isMenuOpen,
        background = zumpaRowColor(row.itemIndex),
        modifier = Modifier.fillMaxWidth(),
        menu = { MessageRowMenu(row, eventHandler) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zumpaRowBackground(row.itemIndex)
                .combinedClickable(
                    interactionSource = null,
                    indication = ripple(),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = time,
                    style = AppTheme.typography.date,
                    color = AppTheme.colorScheme.date,
                )
            }
            //the body was a ?subjectTextSize TextView, not the 13sp the post editor uses
            Text(
                text = body.text,
                inlineContent = body.inlineContent,
                style = AppTheme.typography.subject,
                color = AppTheme.colorScheme.subject,
                modifier = Modifier.padding(top = AppTheme.spaces.small),
            )
        }
    }
}

@Composable
private fun MessageRowMenu(row: SubListRowUiState.Message, eventHandler: SubListEventHandler) {
    RevealRowMenuButton(rememberVectorPainter(Icons.AutoMirrored.Filled.Reply)) {
        eventHandler.onReplyClicked(row.authorReal)
    }
    RevealRowMenuButton(rememberVectorPainter(Icons.Filled.ContentCopy)) {
        eventHandler.onCopyClicked(row.body)
    }
    //`speaker_notes` is the bubble-with-lines the quote button always had
    RevealRowMenuButton(rememberVectorPainter(Icons.Filled.SpeakerNotes)) {
        eventHandler.onQuoteClicked(row.author, row.body)
    }
}

/**
 * `item_sub_list_button.xml`: a `?buttonBackground` Button - orange hairline outline, 5dp corners,
 * no fill - inset from the row edges, with the all-caps middle-ellipsised url the widget produced.
 */
@Composable
private fun LinkRow(row: SubListRowUiState.Link, eventHandler: SubListEventHandler) {
    val label = remember(row.url) { row.url.uppercase(Locale.ROOT) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zumpaRowBackground(row.itemIndex)
            .padding(
                horizontal = AppTheme.spaces.listItemPadding,
                vertical = AppTheme.spaces.tiny,
            ),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.button,
            color = AppTheme.colorScheme.buttonText,
            textAlign = TextAlign.Center,
            maxLines = LINK_MAX_LINES,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppTheme.shapes.button)
                .border(
                    width = AppTheme.sizes.urlButtonStrokeWidth,
                    color = AppTheme.colorScheme.context,
                    shape = AppTheme.shapes.button,
                )
                .clickable(indication = ripple(), interactionSource = null) {
                    eventHandler.onLinkClicked(row.url)
                }
                .padding(AppTheme.spaces.listItemPadding),
        )
    }
}

/**
 * An inline image, which is the one row whose height is not known until the bytes arrive.
 *
 * A plain AsyncImage measures to nothing until then, so the row was simply invisible while it loaded
 * and stayed invisible for good if it failed - and the forum is full of links to pictures that have
 * since gone. So the placeholder holds 16:9 of space and shimmers, and a failure keeps that space
 * and says so instead of collapsing. Once the image is there it takes its own aspect ratio, as
 * before: the 16:9 is a guess for the wait, not a crop.
 *
 * `rememberAsyncImagePainter` rather than SubcomposeAsyncImage - this is a list row, and reading the
 * painter state costs nothing next to subcomposing every one of them.
 */
@Composable
private fun ImageRow(row: SubListRowUiState.Image, eventHandler: SubListEventHandler) {
    val painter = rememberAsyncImagePainter(model = row.url)
    val state by painter.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zumpaRowBackground(row.itemIndex)
            .combinedClickable(
                interactionSource = null,
                indication = ripple(),
                onClick = { eventHandler.onImageClicked(row.url) },
                onLongClick = { eventHandler.onLinkClicked(row.url) },
            )
            .padding(AppTheme.spaces.listItemPadding),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is AsyncImagePainter.State.Error -> ImageRowPlaceholder {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = stringResource(R.string.unable_to_finish_operation),
                    //grey rather than a dimmed orange: nothing here is disabled, it is absent
                    tint = AppTheme.colorScheme.hint,
                    modifier = Modifier.size(AppTheme.sizes.brokenImageIcon),
                )
            }

            is AsyncImagePainter.State.Success -> Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )

            //Empty is the state before the request starts, which for the eye is still waiting
            else -> ImageRowPlaceholder(Modifier.shimmer())
        }
    }
}

/** The space an inline image will take, held while it loads and kept if it never does. */
@Composable
private fun ImageRowPlaceholder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(IMAGE_PLACEHOLDER_RATIO)
            .clip(AppTheme.shapes.button)
            //filled either way, so the slot reads as somewhere a picture goes rather than as a gap
            //with an icon in it. The shimmer paints over this.
            .background(AppTheme.colorScheme.secondaryBackground)
            .then(modifier),
        contentAlignment = Alignment.Center,
        content = { content() },
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
            .clip(AppTheme.shapes.button)
            .background(AppTheme.colorScheme.primaryBackground)
            //survey_button_background_theme_black layers url_button_background over the fill
            .border(
                width = AppTheme.sizes.urlButtonStrokeWidth,
                color = AppTheme.colorScheme.context,
                shape = AppTheme.shapes.button,
            )
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
private fun ReplyPanel(
    uiState: SubListUiState,
    eventHandler: SubListEventHandler,
    bottomInset: Dp = 0.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colorScheme.secondaryBackground)
            .padding(AppTheme.spaces.tiny)
            //inside the background, so the panel colour reaches under the navigation bar
            .padding(bottom = bottomInset),
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
                Icons.AutoMirrored.Filled.Send,
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

private const val REPLY_MAX_LINES = 6

/** Nothing is known about the picture before it lands, and 16:9 is the least surprising guess. */
private const val IMAGE_PLACEHOLDER_RATIO = 16f / 9f
private const val LINK_MAX_LINES = 2

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
