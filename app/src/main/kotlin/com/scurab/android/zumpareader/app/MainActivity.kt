package com.scurab.android.zumpareader.app

import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.v4.app.FragmentTransaction
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import com.pawegio.kandroid.find
import com.pawegio.kandroid.toast
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.content.MainListFragment
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.execIfNull
import java.util.*

/**
 * Created by JBruchanov on 24/11/2015.
 */

public class MainActivity : AppCompatActivity() {

    private val floatingButton by lazy { find<FloatingActionButton>(R.id.fab) }
    private val toolbar by lazy { find<Toolbar>(R.id.toolbar) }
    private val navigationView by lazy { find<NavigationView>(R.id.navigation_view) }
    private val drawerLayout by lazy { find<DrawerLayout>(R.id.drawer_layout) }
    private val navImageView by lazy { find<ImageView>(R.id.navigation_header_image_view) }
    private val progressBar by lazy { find<ProgressBar>(R.id.progress_bar) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)
        floatingButton.setOnClickListener { view -> Toast.makeText(view.context, "Replace with your own action", Toast.LENGTH_LONG).show() }

        supportFragmentManager.findFragmentById(R.id.fragment_container).execIfNull {
            openFragment(MainListFragment(), false)
        }

        navigationView.setNavigationItemSelectedListener { item -> onMenuItemClick(item); false }

    }

    protected fun onMenuItemClick(item: MenuItem) {
        (supportFragmentManager.findFragmentById(R.id.fragment_container) as? BaseFragment).exec {
            if (it.onMenuItemClick(item)) {
                return
            }
        }
        drawerLayout.closeDrawers()
        toast(item.title)
    }

    public val zumpaApp: ZumpaReaderApp
        get() {
            return getApplication() as ZumpaReaderApp
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
        tr.commit()
    }

    public var progressBarVisible: Boolean
        get() {
            return progressBar.visibility == View.VISIBLE
        }
        set(value) {
            progressBar.visibility = if (value) View.VISIBLE else View.GONE
        }
}