package com.scurab.android.zumpareader.ui.nav

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.WindowLayout
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.repository.SelectedThreadStore
import com.scurab.android.zumpareader.ui.compose.LocalNavigator
import com.scurab.android.zumpareader.ui.compose.LocalSharedTransitionScope
import com.scurab.android.zumpareader.ui.image.ImageScreen
import com.scurab.android.zumpareader.ui.main.LaunchPayload
import com.scurab.android.zumpareader.ui.main.MainEffect
import com.scurab.android.zumpareader.ui.main.MainViewModel
import com.scurab.android.zumpareader.ui.mainlist.MainListScreen
import com.scurab.android.zumpareader.ui.offline.OfflineDownloadScreen
import com.scurab.android.zumpareader.ui.post.PostScreen
import com.scurab.android.zumpareader.ui.settings.SettingsScreen
import com.scurab.android.zumpareader.ui.sublist.SubListScreen
import com.scurab.android.zumpareader.ui.tablet.TwoPaneScreen
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The whole navigation graph, and the only host the app has. There are no fragments and no second
 * activity: a screen is a [ZumpaKey] in the back stack and a `NavEntry` that draws it.
 *
 * @param launches whatever the activity was started or re-started with - a push notification tap or
 * a share. The decision of what to do with it belongs to [MainViewModel], so this only forwards it.
 * @param onExit back at the root, which only the activity can act on.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ZumpaNavHost(launches: Flow<LaunchPayload>, onExit: () -> Unit) {
    val context = LocalContext.current
    //not scoped to an entry - this one belongs to the activity, like the intent it reacts to
    val mainViewModel = koinViewModel<MainViewModel>()
    val backStack = rememberNavBackStack(MainListKey)
    val navigator = remember(backStack) { BackStackNavigator(backStack, context, onExit) }

    //the window, not the device: a phone in landscape gets the two panes a tablet gets. Read from
    //the container rather than injected, because this is the one place that recomposes when it
    //changes; the ViewModels get told about it below, since a click has to decide the same thing.
    val windowLayout = koinInject<WindowLayout>()
    val selectedThread = koinInject<SelectedThreadStore>()
    val density = LocalDensity.current
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val widthDp = remember(density, containerWidth) { with(density) { containerWidth.toDp() } }
    val isTwoPane = widthDp.value >= WindowLayout.TWO_PANE_MIN_WIDTH_DP
    //Width alone said "wide enough for two panes, so wide enough for a dialog", and a phone on its
    //side is where those two come apart: it clears the width bar and is barely a third of the
    //height. The post screen is a tab row over a growing text field over an action row, with the
    //keyboard up - a dialog that shape needs height, so it has to ask for height.
    //
    //The height comes from the configuration and not from the container the width comes from,
    //because the container is what the keyboard shrinks. Reading it there would drop a tablet in
    //landscape under the threshold the moment the field was focused, and swap the dialog for a full
    //screen mid-sentence; a configuration height is not touched by the ime.
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val postAsDialog = isTwoPane && screenHeightDp >= WindowLayout.COMPACT_HEIGHT_DP
    LaunchedEffect(widthDp) { windowLayout.onWidthChanged(widthDp.value.toInt()) }

    //crossing the threshold moves the open thread between a pane and a screen of its own, so the
    //back stack has to be fixed up: the same thread must not be visible twice, and must not vanish.
    LaunchedEffect(isTwoPane) {
        if (isTwoPane) {
            backStack.filterIsInstance<SubListKey>().lastOrNull()?.let { key ->
                selectedThread.select(key.threadId)
                backStack.removeAll { it is SubListKey }
            }
        } else {
            val selected = selectedThread.selected.value
            if (selected != null && selectedThread.isExplicit &&
                backStack.none { it is SubListKey }
            ) {
                //under whatever is on top rather than over it, so rotating with a dialog or the
                //settings open still finds the thread waiting when that closes
                backStack.add(1, SubListKey(selected))
            }
            //after the hand-over, never before it: the back stack entry is built out of this. The
            //thread is a screen now, so it is not a selection any more - leaving it set would light
            //a row in the list underneath, which reads as "open" for something that is only behind.
            //Rotating back re-selects from the back stack above, so nothing is lost by dropping it.
            selectedThread.clear()
        }
    }

    LaunchedEffect(Unit) { launches.collect { mainViewModel.onLaunch(it) } }
    LaunchedEffect(Unit) {
        mainViewModel.effects.collect { effect ->
            when (effect) {
                is MainEffect.OpenThread -> navigator.openThread(effect.threadId)
                is MainEffect.OpenPostDialog -> backStack.add(
                    PostKey(
                        subject = effect.subject,
                        message = effect.text,
                        uris = effect.uris.map { it.toString() },
                    )
                )

                is ShowToast -> effect.text?.let { context.toast(it) } ?: context.toast(effect.resId)
                else -> Unit
            }
        }
    }

    //SharedTransitionLayout wraps the whole host because a shared element has to be measured
    //against something both screens are inside. Handed down as a composition local rather than a
    //parameter so a screen that is composed outside a host - a preview, the tablet pane - does not
    //have to take one it cannot be given.
    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalSharedTransitionScope provides this@SharedTransitionLayout,
        ) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = {
                    //the root never gets here, NavDisplay leaves back to the activity instead
                    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    //so koinViewModel() in a screen is scoped to its entry and dies with it
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                sceneStrategies = listOf(DialogSceneStrategy()),
                entryProvider = entryProvider {
                    //one root for both layouts, so rotating keeps the list's ViewModel and its
                    //place in the back stack instead of swapping one root key for another
                    entry<MainListKey> { if (isTwoPane) TwoPaneScreen() else MainListScreen() }
                    entry<SubListKey> { key -> SubListScreen(key.threadId) }
                    entry<ImageKey> { key -> ImageScreen(key.url) }
                    entry<SettingsKey> { SettingsScreen() }
                    entry<OfflineDownloadKey>(metadata = DIALOG) { OfflineDownloadScreen() }
                    //a dialog where there is room for one, a full screen where not
                    entry<PostKey>(metadata = if (postAsDialog) DIALOG else emptyMap()) { key ->
                        PostScreen(key.toArgs(), key.picker)
                    }
                },
            )
        }
    }
}

/**
 * Both dialogs decide for themselves when they may go away - the download dialog blocks back while
 * it is downloading - so a tap outside must not take that decision from them. Back is left alone:
 * the screen's own `BackHandler` sits inside the dialog and wins over it when it wants to.
 */
private val DIALOG = DialogSceneStrategy.dialog(DialogProperties(dismissOnClickOutside = false))
