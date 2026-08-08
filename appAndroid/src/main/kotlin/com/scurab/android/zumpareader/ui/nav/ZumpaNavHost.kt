package com.scurab.android.zumpareader.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.scurab.android.zumpareader.arch.DeviceConfig
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.ui.compose.LocalNavigator
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
@Composable
fun ZumpaNavHost(launches: Flow<LaunchPayload>, onExit: () -> Unit) {
    val context = LocalContext.current
    val device = koinInject<DeviceConfig>()
    //not scoped to an entry - this one belongs to the activity, like the intent it reacts to
    val mainViewModel = koinViewModel<MainViewModel>()
    val backStack = rememberNavBackStack(if (device.isTablet) TwoPaneKey else MainListKey)
    val navigator = remember(backStack) { BackStackNavigator(backStack, context, onExit) }

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

    CompositionLocalProvider(LocalNavigator provides navigator) {
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
                entry<MainListKey> { MainListScreen() }
                entry<TwoPaneKey> { TwoPaneScreen() }
                entry<SubListKey> { key -> SubListScreen(key.threadId) }
                entry<ImageKey> { key -> ImageScreen(key.url) }
                entry<SettingsKey> { SettingsScreen() }
                entry<OfflineDownloadKey>(metadata = DIALOG) { OfflineDownloadScreen() }
                //a dialog on a tablet, a full screen on a phone - as it always was
                entry<PostKey>(metadata = if (device.isTablet) DIALOG else emptyMap()) { key ->
                    PostScreen(key.toArgs(), key.picker)
                }
            },
        )
    }
}

/**
 * Both dialogs decide for themselves when they may go away - the download dialog blocks back while
 * it is downloading - so a tap outside must not take that decision from them. Back is left alone:
 * the screen's own `BackHandler` sits inside the dialog and wins over it when it wants to.
 */
private val DIALOG = DialogSceneStrategy.dialog(DialogProperties(dismissOnClickOutside = false))
