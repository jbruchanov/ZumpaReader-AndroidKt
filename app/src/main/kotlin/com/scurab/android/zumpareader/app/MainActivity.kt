package com.scurab.android.zumpareader.app

import android.content.Intent
import android.net.Uri
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
import com.scurab.android.zumpareader.content.SubListFragment
import com.scurab.android.zumpareader.content.post.PostFragment
import com.scurab.android.zumpareader.ui.DelayClickListener
import com.scurab.android.zumpareader.ui.QuickHideBehavior
import com.scurab.android.zumpareader.ui.hideAnimated
import com.scurab.android.zumpareader.ui.showAnimated
import com.scurab.android.zumpareader.util.*
import io.fabric.sdk.android.Fabric

/**
 * Created by JBruchanov on 24/11/2015.
 */

public class MainActivity : AppCompatActivity() {

    companion object{
        public val PUSH_REQ_CODE = 46879;
        public val EXTRA_THREAD_ID = "ThreadID";
    }

    private val toolbar by lazy { find<Toolbar>(R.id.toolbar) }
    private val progressBar by lazy { find<ProgressBar>(R.id.progress_bar) }
    private val coordinatorLayout by lazy { find<CoordinatorLayout>(R.id.coordinator_layout) }
    private val _floatingButton by lazy { find<FloatingActionButton>(R.id.fab) }

    public val floatingButton: FloatingActionButton  get() = _floatingButton

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

        val color = obtainStyledColor(R.attr.contextColor)
        progressBar.indeterminateDrawable = progressBar.indeterminateDrawable.wrapWithTint(color)
        toolbar.overflowIcon = resources.getDrawable(R.drawable.ic_more).wrapWithTint(color)
        checkIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        checkIntent(intent)
    }

    private fun checkIntent(intent: Intent?) {
        intent.exec {
            val pushThreadId = it.getStringExtra(EXTRA_THREAD_ID)
            if (pushThreadId != null) {
                openFragment(SubListFragment.newInstance(pushThreadId), true, true)
            } else {
                val subject: String? = it.getStringExtra(Intent.EXTRA_SUBJECT)
                val text: String? = it.getStringExtra(Intent.EXTRA_TEXT)
                val uri1 = it.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val uriMore = it.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                var uris: Array<Uri>? = null
                if (uri1 != null) {
                    uris = arrayOf(uri1)
                } else if (uriMore != null) {
                    uris = uriMore.toTypedArray()
                }
                if (!(subject.isNullOrEmpty() && text.isNullOrEmpty() && uris == null)) {
                    if (!zumpaApp.zumpaPrefs.isLoggedIn) {
                        toast(R.string.err_login_first)
                    } else {
                        supportFragmentManager.exec {
                            openFragment(PostFragment.newInstance(subject, text, uris))
                        }
                    }
                }
            }
        }
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
        val containsPostFragment = supportFragmentManager.fragments.firstOrNull() { it -> it is PostFragment } != null
        floatingButton.visibility = if (zumpaApp.zumpaPrefs.isLoggedInNotOffline && !containsPostFragment) View.VISIBLE else View.GONE
    }

    fun onFloatingButtonClick() {
        if (zumpaApp.zumpaPrefs.isLoggedInNotOffline) {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (PostFragment.isRequestCode(requestCode)) {
            (supportFragmentManager.findFragmentByTag(PostFragment::class.java.name) as PostFragment?).execOn {
                onActivityResult(requestCode, resultCode, data)
            }
        }
    }
}