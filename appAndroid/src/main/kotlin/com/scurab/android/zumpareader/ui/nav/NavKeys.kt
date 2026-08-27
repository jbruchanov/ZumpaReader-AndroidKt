package com.scurab.android.zumpareader.ui.nav

import androidx.navigation3.runtime.NavKey
import com.scurab.android.zumpareader.ui.post.PostArgs
import com.scurab.android.zumpareader.ui.post.PostPicker
import kotlinx.serialization.Serializable

/**
 * Every destination in the app. One key per screen, carrying exactly the arguments that screen was
 * opened with - what used to be fragment arguments and intent extras.
 *
 * They are `@Serializable` because `rememberNavBackStack` persists the whole back stack through
 * process death, which is what replaces the FragmentManager's saved state.
 */
sealed interface ZumpaKey : NavKey

/**
 * The root, in both layouts. What it draws depends on how wide the window is - the list alone, or
 * the list with a detail pane - which is a rendering decision rather than a destination, so there
 * is no separate key for it: a rotation would otherwise have to rewrite the bottom of the stack.
 */
@Serializable
data object MainListKey : ZumpaKey

@Serializable
data class SubListKey(val threadId: String) : ZumpaKey

@Serializable
data class ImageKey(val url: String) : ZumpaKey

@Serializable
data object SettingsKey : ZumpaKey

@Serializable
data object OfflineDownloadKey : ZumpaKey

/**
 * Uris travel as strings: `android.net.Uri` is not serializable and the screen only ever hands them
 * back to the platform, so there is nothing to gain from a custom serializer.
 */
@Serializable
data class PostKey(
    val subject: String? = null,
    val message: String? = null,
    val uris: List<String> = emptyList(),
    val threadId: String? = null,
    val picker: PostPicker? = null,
) : ZumpaKey

fun PostKey.toArgs(): PostArgs = PostArgs(
    subject = subject,
    message = message,
    uris = uris.map(android.net.Uri::parse),
    threadId = threadId,
)
