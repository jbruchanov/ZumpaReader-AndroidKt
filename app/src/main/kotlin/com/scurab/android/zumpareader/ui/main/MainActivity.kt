package com.scurab.android.zumpareader.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.arch.collectWhileStarted
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.ui.compose.zumpaContent
import com.scurab.android.zumpareader.ui.mainlist.MainListFragment
import com.scurab.android.zumpareader.ui.post.PostFragment
import com.scurab.android.zumpareader.ui.sublist.SubListFragment
import com.scurab.android.zumpareader.ui.tablet.TabletFragment
import com.scurab.android.zumpareader.util.ifNull
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 24/11/2015.
 *
 * A shell: a fragment container, the back stack, and routing for whatever the app was launched
 * with. Every screen draws its own chrome, so there is no toolbar, progress bar or fab here any
 * more - and once nav-compose lands (C9) the container goes too.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        val PUSH_REQ_CODE = 46879
        val EXTRA_THREAD_ID = "ThreadID"
    }

    private val isTablet by lazy { resources.getBoolean(R.bool.is_tablet) }
    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportFragmentManager.findFragmentById(R.id.fragment_container).ifNull {
            openFragment(if (isTablet) TabletFragment() else MainListFragment(), false)
        }

        viewModel.effects.collectWhileStarted(this) { onEffect(it) }
        viewModel.onLaunch(intent.toLaunchPayload())
    }

    private fun onEffect(effect: UiEffect) {
        when (effect) {
            is MainEffect.OpenThread ->
                openFragment(SubListFragment.newInstance(effect.threadId), true, true)

            is MainEffect.OpenPostDialog ->
                PostFragment
                    .newInstance(
                        effect.subject,
                        effect.text,
                        effect.uris.toTypedArray().takeIf { it.isNotEmpty() },
                    )
                    .show(supportFragmentManager, "PostFragment")

            is ShowToast -> effect.text?.let { toast(it) } ?: toast(effect.resId)
            else -> Unit
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onLaunch(intent.toLaunchPayload())
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

    fun openFragment(fragment: Fragment, addToBackStack: Boolean = true, replace: Boolean = true) {
        val tr = supportFragmentManager.beginTransaction()
        if (addToBackStack) {
            tr.addToBackStack(fragment.javaClass.canonicalName)
        }
        tr.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        if (replace) {
            tr.replace(R.id.fragment_container, fragment, fragment.javaClass.canonicalName)
        } else {
            tr.add(R.id.fragment_container, fragment, fragment.javaClass.canonicalName)
        }
        tr.commitAllowingStateLoss()
    }
}
