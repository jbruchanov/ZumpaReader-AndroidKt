package com.scurab.android.zumpareader.ui.nav

import android.content.Context
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.scurab.android.zumpareader.ui.compose.Navigator
import com.scurab.android.zumpareader.ui.post.PostPicker
import com.scurab.android.zumpareader.util.startLinkActivity

/**
 * [Navigator] over the navigation-3 back stack, which is a plain observable list - navigating is
 * adding a key to it and going back is removing the last one.
 *
 * This is the whole of what replaced the FragmentManager. No screen changed when it did, which is
 * what [Navigator] and `LocalNavigator` existed for.
 */
class BackStackNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val context: Context,
    /** Back at the root leaves the app, and only the activity can do that. */
    private val onExit: () -> Unit,
    /** How to say that a link could not be opened, since the navigator is not itself in compose. */
    private val onLinkError: () -> Unit,
) : Navigator {

    override fun openThread(threadId: String) {
        backStack.add(SubListKey(threadId))
    }

    override fun openImage(url: String) {
        backStack.add(ImageKey(url))
    }

    /** Out of the app entirely, so it is an Intent rather than a key. */
    override fun openLink(url: String) = context.startLinkActivity(url, onLinkError)

    override fun openSettings() {
        backStack.add(SettingsKey)
    }

    override fun openPostDialog(threadId: String?, picker: PostPicker?) {
        backStack.add(PostKey(threadId = threadId, picker = picker))
    }

    override fun openOfflineDownload() {
        backStack.add(OfflineDownloadKey)
    }

    override fun back() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else {
            onExit()
        }
    }
}
