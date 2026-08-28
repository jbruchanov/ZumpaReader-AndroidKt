package com.scurab.android.zumpareader.ui.post

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.drop
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
    //saveable, not remembered: the camera is a separate activity in front of this one, and turning
    //the phone to frame a shot is the obvious thing to do while it is. A plain remember loses the
    //file we told the camera to write to, and the picture then lands nowhere - the callback fires
    //with a null target and drops it. Uri is Parcelable, so the default saver takes it.
    var cameraTarget by rememberSaveable { mutableStateOf<Uri?>(null) }
    //see the LaunchedEffect below - the picker is a one-shot that has to outlive this process
    var pickerConsumed by rememberSaveable { mutableStateOf(false) }
    /*
     * Every picture picked, in order, and saved - so this and not the ViewModel is what remembers
     * them.
     *
     * A camera app is heavy enough to get this process killed, so coming back can mean a fresh
     * ViewModel. When that happened, `start` rebuilt the tabs from the arguments and every picture
     * picked before it was gone - which is why picking three ended with two tabs however many times
     * it was tried. Keeping the list here instead means the ViewModel can be handed the lot and
     * rebuild from it, and being saved state it survives the same death the ViewModel does not.
     *
     * Both pickers go through this. Only the camera used to; the gallery called the ViewModel
     * straight from its callback, and two paths meant to end identically that do not run
     * identically is exactly what shows up as one working and the other not.
     *
     * Strings because that is what saves without a Saver of its own - `fromCamera` first, the
     * uri after it, a shape no uri can collide with.
     */
    var picks by rememberSaveable { mutableStateOf(emptyList<String>()) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraTarget
        cameraTarget = null
        if (saved && uri != null) {
            picks = picks + encodePick(uri, fromCamera = true)
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { picks = picks + encodePick(it, fromCamera = false) }
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
        //Saveable, because the thing being consumed is not: `picker` rides on the PostKey and the
        //back stack saves that, while the ViewModel flag guarding it only lived as long as the
        //ViewModel. Being killed with the camera in front is the ordinary case, not the unlucky one
        //- a camera app is heavy - and coming back to a fresh ViewModel re-read the picker off the
        //restored key and launched the camera again, on top of the picture just taken.
        if (!pickerConsumed) {
            pickerConsumed = true
            vm.onPicker(picker)
        }
    }
    //After the one above, always: declared later, so on a composition that restores a list of
    //picks this runs once `start` has already had its say and cannot undo it. Idempotent, so it
    //costs nothing to run again for a list that has not changed.
    LaunchedEffect(picks) {
        vm.applyPicks(picks.map(::decodePick))
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
            //scrollToPage, not animateScrollToPage: a tab added a frame ago is a page the pager has
            //not measured yet, and an animation towards it has to settle before it counts as
            //arrived - so a picture could land while the pager stayed where it was. A jump is also
            //the right move for a picture just taken, and spares composing every image tab in
            //between, each of which carries a ViewModel.
            pagerState.scrollToPage(index)
        }
    }
    /*
     * pager -> state, for a swipe.
     *
     * `drop(1)` because snapshotFlow hands over the current value the moment it is collected, and
     * that first value is never a swipe - it is just wherever the pager already was. On a fresh
     * composition it is the restored page, which is page zero, so this reported "the message tab is
     * selected" immediately and took the selection back off a picture that had only just set it.
     * That is why a camera picture missed its tab while a gallery one did not: coming back from the
     * camera recreates this composition and coming back from the picker does not, so only the camera
     * got the spurious first emission.
     *
     * settledPage rather than currentPage so a page the pager is merely travelling through is not
     * reported as a choice either.
     */
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page ->
                uiState.tabs.getOrNull(page)?.let { eventHandler.onTabSelected(it.tag) }
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.primaryBackground)
            /*
             * A full screen on a phone, so nothing above this handles the system bars and the tab
             * row would otherwise draw behind the status bar.
             *
             * The bottom is deliberately left alone: the pages below own it, because what belongs
             * there is the keyboard when it is up and the navigation bar when it is not. Padding it
             * here would pin the action row above the navigation bar and then let the keyboard
             * cover it anyway.
             *
             * On a tablet this is a dialog, whose window is already inset by the system, so these
             * all resolve to zero.
             */
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            )
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
                        painter = tab.icon(),
                        contentDescription = null,
                        tint = AppTheme.colorScheme.context,
                    )
                },
            )
        }
    }
}

/** Where the picture came from decides the glyph, which is a screen decision, not a model one. */
@Composable
private fun PostTabUiState.icon(): Painter = when (this) {
    is PostTabUiState.Message -> rememberVectorPainter(Icons.Filled.Create)
    is PostTabUiState.Image ->
        rememberVectorPainter(if (fromCamera) Icons.Filled.PhotoCamera else Icons.Filled.Photo)
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

private fun encodePick(uri: Uri, fromCamera: Boolean): String =
    "${if (fromCamera) "1" else "0"}|$uri"

private fun decodePick(encoded: String): PickedImage = PickedImage(
    uri = encoded.substringAfter('|').toUri(),
    fromCamera = encoded.substringBefore('|') == "1",
)
