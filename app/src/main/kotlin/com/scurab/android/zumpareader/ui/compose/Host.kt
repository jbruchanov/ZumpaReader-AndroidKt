package com.scurab.android.zumpareader.ui.compose

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme

/**
 * The only thing a fragment or an activity does for a compose screen:
 *
 * ```
 * override fun onCreateView(…) = zumpaContent { MainListScreen() }
 * ```
 *
 * Installs the ComposeView, applies [AppTheme] and provides the [Navigator]. When fragments go away
 * and nav-compose takes over, this file is the only thing that changes.
 */
fun Fragment.zumpaContent(content: @Composable () -> Unit): View {
    return ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ZumpaContent(navigator = FragmentNavigator(this@zumpaContent), content = content)
        }
    }
}

fun ComponentActivity.setZumpaContent(content: @Composable () -> Unit) {
    val activity = this
    setContent {
        ZumpaContent(navigator = ActivityNavigator(activity), content = content)
    }
}

@Composable
private fun ZumpaContent(navigator: Navigator, content: @Composable () -> Unit) {
    AppTheme {
        CompositionLocalProvider(LocalNavigator provides navigator, content = content)
    }
}
