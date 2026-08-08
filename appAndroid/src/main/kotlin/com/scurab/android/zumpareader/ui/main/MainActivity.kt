package com.scurab.android.zumpareader.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import com.scurab.android.zumpareader.ui.nav.ZumpaNavHost
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Created by JBruchanov on 24/11/2015.
 *
 * The app's only activity. It owns the window and turns Intents into [LaunchPayload]s; everything
 * else - the back stack, the screens, the chrome - is compose, in [ZumpaNavHost].
 */
class MainActivity : ComponentActivity() {

    companion object {
        val PUSH_REQ_CODE = 46879
        val EXTRA_THREAD_ID = "ThreadID"
    }

    /**
     * Replayed, because `onCreate`'s intent arrives before the composition that consumes it. Later
     * ones from [onNewIntent] simply overwrite it - a payload only matters until it is acted on.
     */
    private val launches = MutableSharedFlow<LaunchPayload>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onLaunchIntent(intent)
        setContent {
            AppTheme {
                ZumpaNavHost(launches = launches, onExit = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        onLaunchIntent(intent)
    }

    /**
     * A relaunch with the same Intent - a rotation is not one, `configChanges` covers that, but a
     * process death is - would otherwise re-open whatever the app was started with. The Intent is
     * consumed so it only ever counts once.
     */
    private fun onLaunchIntent(intent: Intent?) {
        val payload = intent.toLaunchPayload()
        if (payload == LaunchPayload()) return
        intent?.let { setIntent(Intent()) }
        launches.tryEmit(payload)
    }

    /** Intent -> data here, the decision of what to do with it is in the ViewModel. */
    private fun Intent?.toLaunchPayload(): LaunchPayload {
        val intent = this ?: return LaunchPayload()
        intent.getStringExtra(EXTRA_THREAD_ID)?.let { return LaunchPayload(threadId = it) }

        val single = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val multiple = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        return LaunchPayload(
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
            text = intent.getStringExtra(Intent.EXTRA_TEXT),
            uris = when {
                single != null -> listOf(single)
                multiple != null -> multiple.toList()
                else -> emptyList()
            },
        )
    }
}
