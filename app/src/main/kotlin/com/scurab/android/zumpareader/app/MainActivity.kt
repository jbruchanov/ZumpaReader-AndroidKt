package com.scurab.android.zumpareader.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.arch.ShowToast
import com.scurab.android.zumpareader.arch.UiEffect
import com.scurab.android.zumpareader.arch.collectWhileStarted
import com.scurab.android.zumpareader.content.MainListFragment
import com.scurab.android.zumpareader.content.SubListFragment
import com.scurab.android.zumpareader.content.TabletFragment
import com.scurab.android.zumpareader.content.post.PostFragment
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.ui.DelayClickListener
import com.scurab.android.zumpareader.ui.applySystemBarsAsPadding
import com.scurab.android.zumpareader.ui.QuickHideBehavior
import com.scurab.android.zumpareader.ui.hideAnimated
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.ifNull
import com.scurab.android.zumpareader.util.lastNonNullFragment
import com.scurab.android.zumpareader.util.obtainStyledColor
import com.scurab.android.zumpareader.util.wrapWithTint
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 24/11/2015.
 */

class MainActivity : AppCompatActivity() {

    companion object {
        val PUSH_REQ_CODE = 46879
        val EXTRA_THREAD_ID = "ThreadID"
    }

    private val toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }
    private val progressBar by lazy { findViewById<ProgressBar>(R.id.progress_bar) }
    private val coordinatorLayout by lazy { findViewById<CoordinatorLayout>(R.id.coordinator_layout) }
    private val appBar by lazy { findViewById<AppBarLayout>(R.id.app_bar) }
    private val _floatingButton by lazy { findViewById<FloatingActionButton?>(R.id.fab) }
    private val isTablet by lazy { resources.getBoolean(R.bool.is_tablet) }

    val floatingButton: FloatingActionButton get() = _floatingButton!!

    private val viewModel: MainViewModel by viewModel()

    val zumpaApp: ZumpaReaderApp
        get() {
            return application as ZumpaReaderApp
        }

    /**
     * Transitional: the screens that have not been migrated yet still push their loading state in
     * here. Migrated screens let their own ui state drive it through [MainViewModel]. Removed with
     * the last legacy caller in phase 8.
     */
    var progressBarVisible: Boolean
        get() {
            return progressBar.visibility == View.VISIBLE
        }
        set(value) {
            viewModel.setProgressVisible(value)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        onBackPressedDispatcher.addCallback(this, backButtonCallback)
        coordinatorLayout.applySystemBarsAsPadding(topInsetView = appBar)
        setSupportActionBar(toolbar)
        floatingButton.setOnClickListener(DelayClickListener { onFloatingButtonClick() })
        supportFragmentManager.findFragmentById(R.id.fragment_container).ifNull {
            openFragment(if (isTablet) TabletFragment() else MainListFragment(), false)
        }

        val color = obtainStyledColor(R.attr.contextColor)
        progressBar.indeterminateDrawable = progressBar.indeterminateDrawable.wrapWithTint(color)
        toolbar.overflowIcon = resources.getDrawable(R.drawable.ic_more).wrapWithTint(color)

        viewModel.uiState.collectWhileStarted(this) { render(it) }
        viewModel.effects.collectWhileStarted(this) { onEffect(it) }

        viewModel.onLaunch(intent.toLaunchPayload())
    }

    private fun render(state: MainUiState) {
        progressBar.visibility = if (state.isProgressVisible) View.VISIBLE else View.GONE
        _floatingButton?.let { fab ->
            if (state.fab.isVisible) fab.showAnimated() else fab.hideAnimated()
            ((fab.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as QuickHideBehavior?)
                    ?.enabled = state.fab.isScrollHideEnabled
        }
    }

    private fun onEffect(effect: UiEffect) {
        when (effect) {
            is MainEffect.OpenThread ->
                openFragment(SubListFragment.newInstance(effect.threadId), true, true)
            is MainEffect.OpenPostDialog ->
                PostFragment
                        .newInstance(effect.subject, effect.text, effect.uris.toTypedArray().takeIf { it.isNotEmpty() })
                        .show(supportFragmentManager, "PostFragment")
            is ShowToast -> effect.text?.let { toast(it) } ?: toast(effect.resId)
            else -> Unit
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onLaunch(intent.toLaunchPayload())
    }

    /**
     * Intent -> data here, the decision of what to do with it is in the ViewModel.
     */
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
                }
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

        tr.setTransitionStyle(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        tr.commitAllowingStateLoss()
    }

    override fun onResume() {
        super.onResume()
        val containsPostFragment = isTablet ||
                supportFragmentManager.fragments.firstOrNull { it is PostFragment } != null
        //the logged-in/offline half of the old condition comes from the settings flow now
        viewModel.setFabWanted(!containsPostFragment)
    }

    fun onFloatingButtonClick() {
        if (zumpaApp.zumpaPrefs.isLoggedInNotOffline) {
            (supportFragmentManager.fragments.lastNonNullFragment() as? BaseFragment)?.onFloatingButtonClick()
        }
    }

    /**
     * targetSdk 36 enables predictive back, onBackPressed() is not called for back gestures anymore.
     * Registered after super.onCreate() on purpose, callbacks are invoked in reverse order of
     * registration, so this one runs before the FragmentManager's back stack callback, the same
     * way the onBackPressed() override used to.
     */
    private val backButtonCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val handled = (supportFragmentManager.fragments.lastNonNullFragment() as? BaseFragment)
                    ?.onBackButtonClick() ?: false
            if (!handled) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    fun hideFloatingButton() {
        viewModel.setFabWanted(false)
        viewModel.setFabScrollHideEnabled(false)
    }

    fun showFloatingButton() {
        viewModel.setFabWanted(true)
        viewModel.setFabScrollHideEnabled(true)
    }

    fun setScrollStrategyEnabled(enabled: Boolean) {
        viewModel.setFabScrollHideEnabled(enabled)
    }

}
