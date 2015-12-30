package com.scurab.android.zumpareader.app

import android.app.ProgressDialog
import android.os.Bundle
import android.preference.PreferenceActivity
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.model.ZumpaLoginBody
import com.scurab.android.zumpareader.model.ZumpaResponse
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.toast
import retrofit.Callback
import retrofit.Response
import retrofit.Retrofit
import java.util.regex.Pattern

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
    }

    protected fun dispatchLogoutClicked() {
        val prefs = zumpaApp.zumpaPrefs
        prefs.isLoggedIn = false
        prefs.phpSessionId = null
        buttonPref.title = resources.getString(R.string.login)
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
                                val success = it.code() == 302
                                zumpaApp.zumpaPrefs.isLoggedIn = success
                                var sessionId: String? = extractSessionId(it)
                                zumpaApp.zumpaPrefs.phpSessionId = sessionId
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

    private fun extractSessionId(it: Response<ZumpaResponse?>): String? {
        var cookies = it.headers().toMultimap().get("Set-Cookie") as List<String>
        var sessionId: String? = null
        for (c in cookies) {
            if (c.contains("PHPSESSID")) {
                val matcher = Pattern.compile("PHPSESSID=([^;]+);").matcher(c)
                if (matcher.find()) {
                    sessionId = matcher.group(1)
                    break
                }
            }
        }
        return sessionId
    }
}