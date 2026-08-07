package com.scurab.android.zumpareader.ui.post

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scurab.android.zumpareader.BuildConfig
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.HideKeyboard
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.test.Fixtures
import com.scurab.android.zumpareader.test.mock
import com.scurab.android.zumpareader.test.newThread
import com.scurab.android.zumpareader.test.tabs
import com.scurab.android.zumpareader.ui.compose.LocalNavigator
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import com.scurab.android.zumpareader.util.getRandomCameraFileUri
import org.koin.androidx.compose.koinViewModel
import java.io.File

/**
 * The post dialog. [args] and [picker] are the screen's arguments, handed over by the host.
 *
 * Replaces `FragmentTabHost` and its two child fragments - the tabs are pager pages now, so the
 * "only ever grows" workaround (`addedTabTags` / `syncTabs`) is gone with them.
 */
@Composable
fun PostScreen(
    args: PostArgs,
    picker: PostPicker? = null,
    vm: PostViewModel = koinViewModel(),
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraTarget
        cameraTarget = null
        if (saved && uri != null) {
            vm.onImagePicked(uri, fromCamera = true)
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.onImagePicked(it, fromCamera = false) }
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is PostEffect.RequestCameraImage -> runCatching {
                    val file = File(context.getRandomCameraFileUri())
                    val uri = FileProvider.getUriForFile(context, BuildConfig.Authority, file)
                    cameraTarget = uri
                    takePicture.launch(uri)
                }.onFailure { context.toast(R.string.err_fail) }

                is PostEffect.RequestGalleryImage -> runCatching {
                    pickImage.launch("image/*")
                }.onFailure { context.toast(R.string.err_fail) }

                is PostEffect.Dismiss -> navigator.back()
                is HideKeyboard -> keyboard?.hide()
                is ShowToast -> effect.text?.let { context.toast(it) } ?: context.toast(effect.resId)
                else -> Unit
            }
        }
    }
    LaunchedEffect(args) {
        vm.start(args)
        vm.onPicker(picker)
    }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val eventHandler = vm
    PostScreen(uiState, eventHandler)
}

@Composable
private fun PostScreen(uiState: PostUiState, eventHandler: PostEventHandler) {
    val pagerState = rememberPagerState(pageCount = { uiState.tabs.size })

    //state -> pager, for the "a single shared image opens on its tab" case and after a new pick
    LaunchedEffect(uiState.selectedTabTag, uiState.tabs.size) {
        val index = uiState.tabs.indexOfFirst { it.tag == uiState.selectedTabTag }
        if (index >= 0 && index != pagerState.currentPage) {
            pagerState.animateScrollToPage(index)
        }
    }
    //pager -> state, for a swipe
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            uiState.tabs.getOrNull(page)?.let { eventHandler.onTabSelected(it.tag) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.primaryBackground)
    ) {
        PostTabRow(uiState, pagerState.currentPage, eventHandler)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (val tab = uiState.tabs[page]) {
                is PostTabUiState.Message ->
                    if (LocalInspectionMode.current) PagePlaceholder(tab) else PostMessageScreen()

                is PostTabUiState.Image ->
                    if (LocalInspectionMode.current) {
                        PagePlaceholder(tab)
                    } else {
                        PostImageScreen(
                            uri = tab.uri,
                            onLinkUploaded = eventHandler::onImageLinkUploaded,
                            //one ViewModel per image, keyed by the tab it belongs to
                            vm = koinViewModel(key = tab.tag),
                        )
                    }
            }
        }
    }
}

@Composable
private fun PostTabRow(uiState: PostUiState, selectedIndex: Int, eventHandler: PostEventHandler) {
    PrimaryTabRow(
        selectedTabIndex = selectedIndex.coerceIn(0, (uiState.tabs.size - 1).coerceAtLeast(0)),
        containerColor = AppTheme.colorScheme.primaryBackground,
        contentColor = AppTheme.colorScheme.context,
        modifier = Modifier.fillMaxWidth(),
    ) {
        uiState.tabs.forEachIndexed { index, tab ->
            Tab(
                selected = index == selectedIndex,
                onClick = { eventHandler.onTabSelected(tab.tag) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes()),
                        contentDescription = null,
                        tint = AppTheme.colorScheme.context,
                    )
                },
            )
        }
    }
}

private fun PostTabUiState.iconRes(): Int = when (this) {
    is PostTabUiState.Message -> R.drawable.ic_pen_black
    is PostTabUiState.Image -> iconRes
}

/**
 * A preview cannot resolve a ViewModel, so the pages render as labels there. The pages have their
 * own previews in `PostMessageScreen.kt` and `PostImageScreen.kt`.
 */
@Composable
private fun PagePlaceholder(tab: PostTabUiState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = when (tab) {
                is PostTabUiState.Message -> "message"
                is PostTabUiState.Image -> "image ${tab.tag}"
            },
            style = AppTheme.typography.subject,
            color = AppTheme.colorScheme.primaryText,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 320)
@Composable
private fun PostScreenMessageOnlyPreview() = AppTheme {
    PostScreen(Fixtures.Post.newThread(), mock())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 320)
@Composable
private fun PostScreenWithImagesPreview() = AppTheme {
    PostScreen(Fixtures.Post.tabs(), mock())
}
