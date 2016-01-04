package com.scurab.android.zumpareader.app

import android.app.ProgressDialog
import android.os.Bundle
import android.preference.PreferenceActivity
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.model.ZumpaLoginBody
import com.scurab.android.zumpareader.model.ZumpaResponse
import com.scurab.android.zumpareader.util.ParseUtils
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.toast
import retrofit.Callback
import retrofit.Response
import retrofit.Retrofit

/**
 * Created by JBruchanov on 29/12/2015.
 */
public class SettingsActivity : PreferenceActivity() {

    private val buttonPref by lazy { findPreference("prefLogin") }
    public val zumpaApp: ZumpaReaderApp
        get() {
            return getApplication() as ZumpaReaderApp
        }

    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.settings);

        buttonPref.setOnPreferenceClickListener {
            if (zumpaApp.zumpaPrefs.isLoggedIn) {
                dispatchLogoutClicked()
            } else {
                dispatchLoginClicked()
            }
            true
        }
        buttonPref.title = resources.getString(if (zumpaApp.zumpaPrefs.isLoggedIn) R.string.logout else R.string.login)
        zumpaApp.followRedirects = false
    }

    override fun onDestroy() {
        zumpaApp.followRedirects = true
        super.onDestroy()
    }

    protected fun dispatchLogoutClicked() {
        val prefs = zumpaApp.zumpaPrefs
        prefs.isLoggedIn = false
        prefs.cookies = null
        buttonPref.title = resources.getString(R.string.login)
        zumpaApp.resetCookies()
        toast(R.string.done)
    }

    protected fun dispatchLoginClicked() {
        var user = preferenceManager.sharedPreferences.getString("prefUser", "")
        var pwd = preferenceManager.sharedPreferences.getString("prefPassword", "")

        if (user.isNullOrEmpty()) {
            toast(R.string.err_no_username)
            return
        }

        if (pwd.isNullOrEmpty()) {
            toast(R.string.err_no_password)
            return
        }

        showProgressDialog()
        zumpaApp.zumpaAPI.login(ZumpaLoginBody(user, pwd)).enqueue(
                object : Callback<ZumpaResponse?> {
                    override fun onFailure(t: Throwable?) {
                        hideProgressDialog()
                        toast(R.string.err_fail)
                    }

                    override fun onResponse(response: Response<ZumpaResponse?>?, retrofit: Retrofit?) {
                        hideProgressDialog()
                        if (!isFinishing) {
                            response.exec {
                                val success = response?.code() == 302//response?.body()?.asString()?.contains("/logout.php") ?: false
                                zumpaApp.zumpaPrefs.isLoggedIn = success
                                var sessionId: String? = ParseUtils.extractSessionId(it)
                                zumpaApp.zumpaPrefs.cookies = ParseUtils.extractCookies(it)
                                toast(if (success) R.string.ok else R.string.err_fail)
                                if (success) {
                                    buttonPref.title = resources.getString(R.string.logout)
                                }
                            }
                        }
                    }
                }
        )
    }

    override fun onPause() {
        super.onPause()
        hideProgressDialog()
        zumpaApp.zumpaParser.userName = zumpaApp.zumpaPrefs.loggedUserName
    }

    private fun showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = ProgressDialog.show(this, null, resources.getString(R.string.wheeeee), true, false)
        }
    }

    private fun hideProgressDialog() {
        progressDialog?.cancel()
        progressDialog = null
    }
}