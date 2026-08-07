package com.scurab.android.zumpareader.ui.compose

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ui.image.ImageActivity
import com.scurab.android.zumpareader.ui.main.MainActivity
import com.scurab.android.zumpareader.ui.offline.OfflineDownloadFragment
import com.scurab.android.zumpareader.ui.post.PostFragment
import com.scurab.android.zumpareader.ui.settings.SettingsActivity
import com.scurab.android.zumpareader.ui.sublist.SubListFragment
import com.scurab.android.zumpareader.util.startLinkActivity

/**
 * [Navigator] over the fragment stack, i.e. the arrangement that exists today. Replaced wholesale by
 * a nav-compose implementation once the last screen is converted; nothing inside a screen changes
 * when that happens.
 */
class FragmentNavigator(private val fragment: Fragment) : Navigator {

    private val mainActivity: MainActivity? get() = fragment.activity as? MainActivity
    private val isTablet: Boolean get() = fragment.resources.getBoolean(R.bool.is_tablet)

    override fun openThread(threadId: String) {
        mainActivity?.openFragment(SubListFragment.newInstance(threadId), true, true)
    }

    override fun openImage(url: String) {
        val activity = fragment.activity ?: return
        activity.startActivity(ImageActivity.createIntent(activity, url))
    }

    override fun openLink(url: String) {
        fragment.context?.startLinkActivity(url)
    }

    override fun openSettings() {
        fragment.context?.let { it.startActivity(Intent(it, SettingsActivity::class.java)) }
    }

    override fun openPostDialog(threadId: String?, flag: Int?) {
        val post = if (threadId == null && flag == null) {
            PostFragment()
        } else {
            PostFragment.newInstance(null, null, null, threadId, flag ?: 0)
        }
        if (isTablet) {
            post.show(fragment.childFragmentManager, POST_TAG)
        } else {
            mainActivity?.openFragment(post, true, false)
        }
    }

    override fun openOfflineDownload() {
        mainActivity?.supportFragmentManager?.let {
            OfflineDownloadFragment().show(it, OfflineDownloadFragment::class.java.name)
        }
    }

    override fun back() {
        mainActivity?.onBackPressedDispatcher?.onBackPressed()
    }

    private companion object {
        const val POST_TAG = "PostFragment"
    }
}

/**
 * For an activity that hosts a compose screen directly and is not part of the fragment stack -
 * [ImageActivity] is the only one. The operations it cannot perform throw rather than silently doing
 * nothing, so a mis-wired host fails loudly in development.
 */
class ActivityNavigator(private val activity: ComponentActivity) : Navigator {

    override fun openLink(url: String) = activity.startLinkActivity(url)

    /**
     * `finish()`, not `onBackPressedDispatcher.onBackPressed()`: a screen that installs a
     * `BackHandler` and answers it by calling [back] would otherwise dispatch straight back into
     * its own handler.
     */
    override fun back() {
        activity.finish()
    }

    override fun openThread(threadId: String) = unsupported("openThread")

    override fun openImage(url: String) = unsupported("openImage")

    override fun openSettings() = unsupported("openSettings")

    override fun openPostDialog(threadId: String?, flag: Int?) = unsupported("openPostDialog")

    override fun openOfflineDownload() = unsupported("openOfflineDownload")

    private fun unsupported(name: String): Nothing =
        throw UnsupportedOperationException("$name is not reachable from ${activity.javaClass.simpleName}")
}
