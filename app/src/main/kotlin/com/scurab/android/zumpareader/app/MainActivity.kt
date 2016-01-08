package com.scurab.android.zumpareader.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import com.crashlytics.android.Crashlytics
import com.pawegio.kandroid.find
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.content.MainListFragment
import com.scurab.android.zumpareader.ui.DelayClickListener
import com.scurab.android.zumpareader.ui.QuickHideBehavior
import com.scurab.android.zumpareader.ui.hideAnimated
import com.scurab.android.zumpareader.util.*
import io.fabric.sdk.android.Fabric

/**
 * Created by JBruchanov on 24/11/2015.
 */

public class MainActivity : AppCompatActivity() {

    private val toolbar by lazy { find<Toolbar>(R.id.toolbar) }
    private val progressBar by lazy { find<ProgressBar>(R.id.progress_bar) }
    private val coordinatorLayout by lazy { find<CoordinatorLayout>(R.id.coordinator_layout) }
    private val _floatingButton by lazy { find<FloatingActionButton>(R.id.fab) }

    public val floatingButton: FloatingActionButton  get() = _floatingButton

    private val _settingsButton by lazy { find<ImageButton>(R.id.settings) }
    public val settingsButton: ImageButton get() = _settingsButton

    public val zumpaApp: ZumpaReaderApp
        get() {
            return getApplication() as ZumpaReaderApp
        }

    public var progressBarVisible: Boolean
        get() {
            return progressBar.visibility == View.VISIBLE
        }
        set(value) {
            progressBar.visibility = if (value) View.VISIBLE else View.GONE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Fabric.with(this, Crashlytics());

        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)
        floatingButton.setOnClickListener(DelayClickListener() { onFloatingButtonClick() })
        supportFragmentManager.findFragmentById(R.id.fragment_container).execIfNull {
            openFragment(MainListFragment(), false)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val color = obtainStyledColor(R.attr.contextColor)
            settingsButton.setImageDrawable(_settingsButton.drawable.wrapWithTint(color))
        }

        settingsButton.setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
    }

    public fun openFragment(fragment: BaseFragment, addToBackStack: Boolean = true, replace: Boolean = true) {
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
        tr.commit()
    }

    override fun onResume() {
        super.onResume()
        floatingButton.visibility = if (zumpaApp.zumpaPrefs.isLoggedIn) View.VISIBLE else View.GONE
    }

    fun onFloatingButtonClick() {
        if (zumpaApp.zumpaPrefs.isLoggedIn) {
            (supportFragmentManager.fragments.lastNonNullFragment() as? BaseFragment).exec {
                it.onFloatingButtonClick();
            }
        }
    }

    override fun onBackPressed() {
        (supportFragmentManager.fragments.lastNonNullFragment() as? BaseFragment).exec {
            if (!it.onBackButtonClick()) {
                super.onBackPressed()
            }
            return
        }
        super.onBackPressed()
    }

    fun hideFloatingButton() {
        floatingButton.hideAnimated()
        ((floatingButton.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as QuickHideBehavior?)?.enabled = false;
    }

    fun showFloatingButton() {
        floatingButton.showAnimated()
        ((floatingButton.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as QuickHideBehavior?)?.enabled = true;
    }


    public fun setScrollStrategyEnabled(enabled: Boolean) {
        ((floatingButton.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as QuickHideBehavior?)?.enabled = enabled;
    }

    fun reloadData() {
        var fragment = supportFragmentManager.fragments.firstOrNull { it -> it is MainListFragment } as? MainListFragment
        fragment.execOn { reloadData() }
    }
}