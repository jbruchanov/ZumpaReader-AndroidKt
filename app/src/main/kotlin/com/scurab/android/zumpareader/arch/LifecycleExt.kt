package com.scurab.android.zumpareader.arch

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collects while the owner is at least STARTED and stops collecting when it is not.
 *
 * This replaces `BaseFragment.launchWithView()`. The difference matters: `launchWithView` bound the
 * *work* to the view and kept it running in the background, which is why the screens had to abandon
 * a load in `onPause` to stop it from touching the ui. Here the work lives in `viewModelScope` and
 * keeps running, only the collection pauses, so a load that finishes while the screen is stopped is
 * rendered when it comes back.
 */
fun <T> Flow<T>.collectWhileStarted(owner: LifecycleOwner, block: suspend (T) -> Unit): Job {
    return owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect(block)
        }
    }
}
