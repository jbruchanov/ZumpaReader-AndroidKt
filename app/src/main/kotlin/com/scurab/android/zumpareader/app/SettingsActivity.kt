package com.scurab.android.zumpareader.app

import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceActivity
import com.google.android.gms.gcm.GoogleCloudMessaging
import com.google.android.gms.iid.InstanceID
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.content.SendingFragment
import com.scurab.android.zumpareader.model.ZumpaLoginBody
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.*
import java.net.URI

/**
 * Created by JBruchanov on 29/12/2015.
 */
public class SettingsActivity : PreferenceActivity(), SendingFragment {


    private val buttonPref by lazy { findPreference(ZumpaPrefs.KEY_LOGIN) }
    private val showLastAuthorPref by lazy { findPreference(ZumpaPrefs.KEY_SHOW_LAST_AUTHOR) }
    private val filterPref by lazy { findPreference(ZumpaPrefs.KEY_FILTER) }

    public val zumpaApp: ZumpaReaderApp
        get() {
            return application as ZumpaReaderApp
        }

    private var progressDialog: ProgressDialog? = null
    override var sendingDialog: ProgressDialog? = null

    override fun getContext(): Context {
        return this
    }

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
        filterPref.isEnabled = zumpaApp.zumpaPrefs.isLoggedIn
    }

    protected fun dispatchLogoutClicked() {
        val prefs = zumpaApp.zumpaPrefs
        var user = prefs.loggedUserName
        prefs.isLoggedIn = false
        prefs.cookies = null
        buttonPref.title = resources.getString(R.string.login)
        zumpaApp.resetCookies()
        filterPref.isEnabled = false
        if (user != null) {
            isSending = true
            object : LogoutTask(zumpaApp, user) {
                override fun onPostExecute(loginResult: Boolean, pushResult: Boolean) {
                    toast(R.string.done)
                    isSending = false
                }
            }.execute()
        } else {
            toast(R.string.done)
        }
    }

    protected fun dispatchLoginClicked() {
        var user = preferenceManager.sharedPreferences.getString(ZumpaPrefs.KEY_USER_NAME, "")
        var pwd = preferenceManager.sharedPreferences.getString(ZumpaPrefs.KEY_PASSWORD, "")

        if (user.isNullOrEmpty()) {
            toast(R.string.err_no_username)
            return
        }

        if (pwd.isNullOrEmpty()) {
            toast(R.string.err_no_password)
            return
        }

        isSending = true
        object : LoginTask(zumpaApp, ZumpaLoginBody(user, pwd)){
            override fun onPostExecute(loginResult: Boolean, pushResult: Boolean) {
                isSending = false
                if (!isFinishing) {
                    toast(if (loginResult) R.string.ok else R.string.err_fail)
                    if (!pushResult) {
                        toast(R.string.err_no_push_reg)
                    }
                    filterPref.isEnabled = loginResult
                    if (loginResult) {
                        buttonPref.title = resources.getString(R.string.logout)
                    }
                }
            }
        }.execute()
    }

    override fun onResume() {
        super.onResume()
        zumpaApp.followRedirects = false
    }

    override fun onPause() {
        super.onPause()
        isSending = false

        zumpaApp.zumpaParser.execOn {
            userName = zumpaApp.zumpaPrefs.loggedUserName
            isShowLastUser = zumpaApp.zumpaPrefs.showLastAuthor
        }
        zumpaApp.cookieManager.cookieStore.removeAll()
        zumpaApp.cookieManager.put(URI.create(ZR.Constants.ZUMPA_MAIN_URL), zumpaApp.zumpaPrefs.cookiesMap);
        zumpaApp.zumpaParser.isShowLastUser = zumpaApp.zumpaPrefs.showLastAuthor
        zumpaApp.followRedirects = true
    }
}

private abstract class LoginTask(private val zumpaApp: ZumpaReaderApp, private val zumpaLoginBody: ZumpaLoginBody) : AsyncTask<Void, Void, Void>() {
    private var loginResult: Boolean = false
    private var pushResult: Boolean = false

    override fun doInBackground(vararg params: Void?): Void? {
        zumpaApp.followRedirects = false

        val loginResponse = zumpaApp.zumpaOnlineAPI.login(zumpaLoginBody).execute()
        loginResult = loginResponse.code() == 302

        zumpaApp.followRedirects = true
        zumpaApp.zumpaPrefs.isLoggedIn = loginResult
        zumpaApp.zumpaPrefs.cookies = if (loginResult) ParseUtils.extractCookies(loginResponse) else null

        try {
            val instanceID = InstanceID.getInstance(zumpaApp);
            val token = instanceID.getToken("542579595500", GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
            zumpaApp.zumpaPrefs.pushRegId = token
            if (token != null && loginResult) {
                val body = zumpaApp.zumpaOnlineAPI.getMainPageHtml().execute().body().asString()
                val uid = ZumpaSimpleParser.parseUID(body)
                if (uid != null) {
                    val response = zumpaApp.zumpaPHPAPI.register(zumpaLoginBody.nick, uid, token).execute().body().asUTFString()
                    pushResult = "[OK]".equals(response)
                }
            }
        } catch(e: Throwable) {
            e.printStackTrace()
        }
        return null
    }

    final override fun onPostExecute(result: Void?) {
        onPostExecute(loginResult, pushResult)
    }

    abstract fun onPostExecute(loginResult: Boolean, pushResult: Boolean)
}

private abstract class LogoutTask(private val zumpaApp: ZumpaReaderApp, private val zumpaUser: String) : AsyncTask<Void, Void, Void>() {
    private var logoutResult: Boolean = false
    private var pushResult: Boolean = false

    override fun doInBackground(vararg params: Void?): Void? {
        try {
            val response = zumpaApp.zumpaPHPAPI.unregister(zumpaUser).execute().body().asUTFString()
            pushResult = "[OK]".equals(response)
        } catch(e: Throwable) {
            e.printStackTrace()
        }
        return null
    }

    final override fun onPostExecute(result: Void?) {
        onPostExecute(logoutResult, pushResult)
    }

    abstract fun onPostExecute(loginResult: Boolean, pushResult: Boolean)
}