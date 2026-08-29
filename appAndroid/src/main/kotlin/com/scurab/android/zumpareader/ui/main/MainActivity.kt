package com.scurab.android.zumpareader.ui.main

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scurab.android.zumpareader.arch.WindowLayout
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import com.scurab.android.zumpareader.ui.nav.ZumpaNavHost
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.android.ext.android.inject

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

    private val windowLayout: WindowLayout by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        //the app draws its own chrome over the system bars - see the translucent top bars. `dark`
        //rather than the default so the icons stay light even when the phone is in light mode: the
        //only theme this app has is black.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        //before the first composition, so the list ViewModel's first load already knows whether it
        //has a detail pane to select a thread into
        windowLayout.onWidthChanged(resources.configuration.screenWidthDp)
        //only on a genuinely fresh start: a recreation - a rotation is one now - re-delivers the
        //Intent the app was started with, and that payload has already been acted on
        if (savedInstanceState == null) {
            onLaunchIntent(intent)
        }
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
     * The Intent is consumed as well as guarded by the saved state, for the case the saved state
     * does not survive: a payload only ever counts once, or a notification tap would re-open its
     * thread every time the activity came back.
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
