package com.scurab.android.zumpareader.ui.sublist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SpeakerNotes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
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
import com.scurab.android.zumpareader.arch.WindowLayout
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
import com.scurab.android.zumpareader.ui.compose.sharedImage
import com.scurab.android.zumpareader.ui.compose.shimmer
import com.scurab.android.zumpareader.ui.compose.ActionIcon
import com.scurab.android.zumpareader.ui.compose.UrlButton
import com.scurab.android.zumpareader.ui.compose.zumpaRowBackground
import com.scurab.android.zumpareader.ui.compose.zumpaRowColor
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import com.scurab.android.zumpareader.util.saveToClipboard
import org.koin.androidx.compose.koinViewModel
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
                is SubListEffect.ScrollToBottom -> listState.animateScrollToItem(effect.index)

                is SubListEffect.ScrollToTop -> listState.animateScrollToItem(0)
                is SubListEffect.OpenThread -> navigator.openThread(effect.threadId)
                is SubListEffect.OpenImage -> navigator.openImage(effect.url)
                is SubListEffect.OpenLink -> navigator.openLink(effect.url)
                is SubListEffect.OpenPostDialog ->
                    navigator.openPostDialog(effect.threadId, effect.picker)
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
    val fabVisible = uiState.canPost && !uiState.isPostPanelVisible
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
                            //Clip rather than Ellipsis: the marquee measures the text unbounded, so
                            //nothing overflows for an ellipsis to shorten anyway.
                            overflow = TextOverflow.Clip,
                            //A subject too long for the bar scrolls past instead of being cut off
                            //with a full stop where the interesting half was. Does nothing at all
                            //when the subject fits, so a short one simply sits still, and pauses
                            //between passes so the beginning can be read before it moves off.
                            //
                            //The end padding is because M3 insets the title by 16dp at the start
                            //but only by however wide the actions are at the end - and this bar
                            //has none, so the text ran to the edge on one side and sat 16dp in
                            //on the other. 12dp, because M3 already puts 4dp on both ends of the
                            //title slot. Outside the marquee, so the text scrolls within the
                            //padding rather than through it.
                            modifier = Modifier
                                .padding(end = AppTheme.spaces.mid)
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    velocity = MARQUEE_VELOCITY,
                                ),
                        )
                    },
                    expandedHeight = AppTheme.sizes.topBarHeight,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    //safeDrawing, not the systemBarsForVisualComponents the default uses: that
                    //one leaves out the display cutout, which in landscape is exactly the inset on
                    //the side the title runs into. Applied here and only here - the Box outside
                    //keeps its background running edge to edge and under the status bar.
                    windowInsets = WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                )
                //A line between the header and what scrolls under it. Under the progress
                //strip in the stack on purpose: while a load is running that strip is the
                //separator, and the two on top of each other would only thicken it.
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    thickness = AppTheme.sizes.divider,
                    color = AppTheme.colorScheme.context,
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
            //no QuickHideFab here, unlike the list: the way to answer a thread should not be
            //something you have to stop scrolling to get back. The list leaves room for it instead
            //of sliding it out of the way - see fabSpace below.
            if (fabVisible) {
                FloatingActionButton(
                    onClick = eventHandler::onPostPanelRequested,
                    containerColor = AppTheme.colorScheme.context,
                    //white on the orange, like the plus on the list - the icon takes this as its
                    //LocalContentColor, so both fabs read the same way
                    contentColor = AppTheme.colorScheme.primaryText,
                    //M3 draws a squircle, the old Material fab was round
                    shape = CircleShape,
                ) {
                    //the same plus the list has, not a pen: one fab, one meaning
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        },
    ) { padding ->
        //one inset, applied once: whatever is at the bottom of the screen takes it - the reply panel
        //when it is up, otherwise the list. Adding imePadding on top of this is what counted twice.
        val bottomInset = padding.calculateBottomPadding()
        //what the fab occupies, now that it stays put: its own height plus the margin the Scaffold
        //places it with, on each side of it
        val fabSpace = if (fabVisible) {
            AppTheme.sizes.fabSize + AppTheme.spaces.fabMargin * 2
        } else {
            0.dp
        }
        //the side insets stop here and travel down to the rows instead. Putting them on the
        //LazyColumn - as contentPadding or as a modifier - would inset the alternating background
        //with the text, and in landscape that leaves a bare stripe of window down one side.
        val layoutDirection = LocalLayoutDirection.current
        val rowPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            end = padding.calculateEndPadding(layoutDirection),
        )

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
                        //the fab no longer moves out of the way, so the list ends above it -
                        //otherwise the last message sits under it and cannot be read
                        bottom = (if (uiState.isPostPanelVisible) 0.dp else bottomInset) + fabSpace,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
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
                        SubListRow(row, rowPadding, eventHandler)
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
                ReplyPanel(uiState, eventHandler, bottomInset, rowPadding)
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
private fun SubListRow(
    row: SubListRowUiState,
    contentPadding: PaddingValues,
    eventHandler: SubListEventHandler,
) {
    when (row) {
        is SubListRowUiState.Message -> MessageRow(row, contentPadding, eventHandler)
        is SubListRowUiState.Link -> LinkRow(row, contentPadding, eventHandler)
        is SubListRowUiState.Image -> ImageRow(row, contentPadding, eventHandler)
        is SubListRowUiState.Survey -> SurveyCard(row.survey, contentPadding, eventHandler)
    }
}

@Composable
private fun MessageRow(
    row: SubListRowUiState.Message,
    contentPadding: PaddingValues,
    eventHandler: SubListEventHandler,
) {
    val renderer = rememberAnnotatedTextRenderer()
    val body = remember(row.body, renderer) { renderer.body(row.body) }
    val author = remember(row.author, row.rating, renderer) { renderer.author(row.author, row.rating) }
    val time = remember(row.time) { row.time.formatPostTime() }

    RevealRow(
        isOpen = row.isMenuOpen,
        background = zumpaRowColor(row.itemIndex),
        modifier = Modifier.fillMaxWidth(),
        menuStartPadding = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
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
                //after the background and the ripple, so both still run to the window edge
                .padding(contentPadding)
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
private fun LinkRow(
    row: SubListRowUiState.Link,
    contentPadding: PaddingValues,
    eventHandler: SubListEventHandler,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zumpaRowBackground(row.itemIndex)
            .padding(contentPadding)
            .padding(
                horizontal = AppTheme.spaces.listItemPadding,
                vertical = AppTheme.spaces.tiny,
            ),
    ) {
        UrlButton(
            url = row.url,
            onLongClick = { eventHandler.onLinkLongPressed(row.url) },
        ) { eventHandler.onLinkClicked(row.url) }
    }
}

@Composable
private fun ImageRow(
    row: SubListRowUiState.Image,
    contentPadding: PaddingValues,
    eventHandler: SubListEventHandler,
) {
    val painter = rememberAsyncImagePainter(model = row.url)
    val state by painter.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zumpaRowBackground(row.itemIndex)
            .padding(contentPadding)
            .padding(AppTheme.spaces.listItemPadding),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            //nothing to show and nothing more to wait for, so all that is left is the address as
            //something to press - rather than 16:9 of empty space around a broken-picture icon.
            //A picture that loaded speaks for itself and gets no caption.
            is AsyncImagePainter.State.Error -> UrlButton(
                url = row.url,
                onLongClick = { eventHandler.onLinkLongPressed(row.url) },
            ) { eventHandler.onLinkClicked(row.url) }

            //the loaded picture is the one that flies to the viewer, so the shared element goes
            //here rather than on the row: the placeholder has nothing worth animating
            is AsyncImagePainter.State.Success -> Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppTheme.shapes.button)
                    //hold the picture to copy its address - the only way to it now that a
                    //picture which loaded carries no caption
                    .combinedClickable(
                        indication = ripple(),
                        interactionSource = null,
                        onLongClick = { eventHandler.onLinkLongPressed(row.url) },
                        onClick = { eventHandler.onImageClicked(row.url) },
                    )
                    .sharedImage(row.url),
            )

            //Empty is the state before the request starts, which for the eye is still waiting
            else -> ImageRowPlaceholder(Modifier.shimmer())
        }
    }
}

/** The space an inline image will take, held while it loads. A failed load keeps nothing. */
@Composable
private fun ImageRowPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(IMAGE_PLACEHOLDER_RATIO)
            .clip(AppTheme.shapes.button)
            //filled, so the slot reads as somewhere a picture goes rather than as a gap. The
            //shimmer paints over this.
            .background(AppTheme.colorScheme.secondaryBackground)
            .then(modifier),
    )
}

@Composable
private fun SurveyCard(
    survey: SurveyUiState,
    contentPadding: PaddingValues,
    eventHandler: SubListEventHandler,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colorScheme.secondaryBackground)
            .padding(contentPadding)
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
        //The vote share, as the level drawable used to be - but only behind the option that was
        //voted for. Every option carrying a tint made the whole survey look answered several times
        //over, and the share is in each label as a number anyway, so the bar says nothing the row
        //does not. The one that was voted for takes the lighter of the two tints, not the heavier.
        if (item.voted) {
            Box(
                Modifier
                    .fillMaxWidth(item.percents / 100f)
                    .matchParentSize()
                    .background(AppTheme.colorScheme.context25p, AppTheme.shapes.button)
            )
        }
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
    contentPadding: PaddingValues = PaddingValues(),
) {
    //The shape widget_post_message.xml had, which this panel used to be an instance of: the field on
    //a line of its own, then the buttons underneath - photo and camera at the start, send at the
    //end. A short window - a phone in landscape - puts them all on one row instead, because the
    //height a second row costs is the height the thread has left to show above the keyboard.
    val isCompactHeight = LocalConfiguration.current.screenHeightDp < WindowLayout.COMPACT_HEIGHT_DP

    Column(
        modifier = Modifier
            .fillMaxWidth()
            //primaryBackground, like the full screen version of the same thing. This was
            //secondaryBackground - #202020 - which against a black app reads as a grey panel, so
            //writing a reply here and writing one on the post screen did not look alike.
            .background(AppTheme.colorScheme.primaryBackground)
            .padding(AppTheme.spaces.tiny)
            //inside the background, so the panel colour reaches under the navigation bar - the
            //side insets go here for the same reason, or the field slides under a landscape one
            .padding(bottom = bottomInset)
            .padding(contentPadding),
    ) {
        if (isCompactHeight) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                //the field takes the slack, so the buttons need no spacer to sit at the end
                ReplyField(uiState, eventHandler, Modifier.weight(1f))
                ReplyActionIcons(uiState, eventHandler, spaced = false)
            }
        } else {
            ReplyField(uiState, eventHandler, Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth()) {
                ReplyActionIcons(uiState, eventHandler, spaced = true)
            }
        }
    }
}

/**
 * A BasicTextField rather than an OutlinedTextField: the latter carries ~16dp of padding of its own
 * and a 56dp floor before any of ours, which was the bulk of what was there. This is the legacy
 * field - a rounded rect, `gap_small` inside it, `response_edit_text_min_height` tall - and nothing
 * else.
 */
@Composable
private fun ReplyField(
    uiState: SubListUiState,
    eventHandler: SubListEventHandler,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = AppTheme.sizes.responseEditTextMinHeight)
            .border(
                width = AppTheme.sizes.urlButtonStrokeWidth,
                //full orange, like every other outline the app draws - context25p left it as a
                //hint of a box rather than a box
                color = AppTheme.colorScheme.context,
                shape = AppTheme.shapes.editText,
            )
            .padding(AppTheme.spaces.small),
        //centred while it is one line, and it grows downwards from there
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = uiState.draft.text,
            onValueChange = eventHandler::onDraftChanged,
            enabled = !uiState.isSending,
            //primaryText, not colorScheme.message: that one is Black, because the legacy field it
            //came from was a solid white rounded rect. This field is dark, so the text on it is
            //white like the rest of the app. BasicTextField does not read LocalTextStyle, so it has
            //to be said here rather than inherited.
            textStyle = AppTheme.typography.message.copy(
                color = AppTheme.colorScheme.primaryText,
            ),
            cursorBrush = SolidColor(AppTheme.colorScheme.context),
            maxLines = REPLY_MAX_LINES,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * @param spaced pushes send to the far end. Wanted when the icons are a row of their own, not when
 * the field beside them already takes the slack.
 */
@Composable
private fun RowScope.ReplyActionIcons(
    uiState: SubListUiState,
    eventHandler: SubListEventHandler,
    spaced: Boolean,
) {
    //both open the post screen on this thread with that picker, which is what the old buttons did
    //through onOpenPostFragment(R.id.photo)
    ActionIcon(
        icon = rememberVectorPainter(Icons.Filled.Photo),
        enabled = !uiState.isSending,
        onClick = eventHandler::onReplyPhotoClicked,
    )
    ActionIcon(
        icon = rememberVectorPainter(Icons.Filled.PhotoCamera),
        enabled = !uiState.isSending,
        onClick = eventHandler::onReplyCameraClicked,
    )
    if (spaced) Spacer(Modifier.weight(1f))
    ActionIcon(
        icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Send),
        enabled = !uiState.isSending && !uiState.draft.isBlank,
        onClick = eventHandler::onSendClicked,
    )
}

/** Slower than the 30dp/s default, so a long subject can be read rather than watched. */
private val MARQUEE_VELOCITY = 20.dp

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
    MessageRow(Fixtures.SubList.message(isMenuOpen = true), PaddingValues(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SurveyCardPreview() = AppTheme {
    SurveyCard(Fixtures.SubList.survey(), PaddingValues(), mock())
}
