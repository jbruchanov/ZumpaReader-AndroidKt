package com.scurab.android.zumpareader.app

import android.app.ProgressDialog
import android.content.Context
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
import com.scurab.android.zumpareader.util.ParseUtils
import com.scurab.android.zumpareader.util.ZumpaPrefs
import com.scurab.android.zumpareader.util.execOn
import com.scurab.android.zumpareader.util.toast
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.net.URI

/**
 * Created by JBruchanov on 29/12/2015.
 */
class SettingsActivity : PreferenceActivity(), SendingFragment {


    private val buttonPref by lazy { findPreference(ZumpaPrefs.KEY_LOGIN) }
    private val showLastAuthorPref by lazy { findPreference(ZumpaPrefs.KEY_SHOW_LAST_AUTHOR) }
    private val filterPref by lazy { findPreference(ZumpaPrefs.KEY_FILTER) }

    val zumpaApp: ZumpaReaderApp
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
        addPreferencesFromResource(R.xml.settings)

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
            logoutCall = LogoutCall(zumpaApp, user)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { result, err ->
                        isSending = false
                        if (err == null) {
                            toast(R.string.done)
                        } else {
                            toast(err.message)
                        }
                    }
        } else {
            toast(R.string.done)
        }
    }

    private var loginCall: Disposable? = null
    private var logoutCall: Disposable? = null

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
        loginCall = LoginCall(zumpaApp, ZumpaLoginBody(user, pwd))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result ->
                            isSending = false
                            val loginResult = result.first
                            val pushResult = result.second
                            toast(if (loginResult) R.string.ok else R.string.err_fail)
                            if (!pushResult) {
                                toast(R.string.err_no_push_reg)
                            }
                            filterPref.isEnabled = loginResult
                            if (loginResult) {
                                buttonPref.title = resources.getString(R.string.logout)
                            }
                        },
                        { err ->
                            isSending = false
                            toast(err.message)
                        }
                )
    }

    override fun onPause() {
        super.onPause()
        isSending = false

        loginCall?.dispose()
        logoutCall?.dispose()

        zumpaApp.zumpaParser.execOn {
            userName = zumpaApp.zumpaPrefs.loggedUserName
            isShowLastUser = zumpaApp.zumpaPrefs.showLastAuthor
        }
        zumpaApp.cookieManager.cookieStore.removeAll()
        zumpaApp.cookieManager.put(URI.create(ZR.Constants.ZUMPA_MAIN_URL), zumpaApp.zumpaPrefs.cookiesMap)
        zumpaApp.zumpaParser.isShowLastUser = zumpaApp.zumpaPrefs.showLastAuthor
    }
}

private class LoginCall(private val zumpaApp: ZumpaReaderApp, private val zumpaLoginBody: ZumpaLoginBody) : Single<Pair<Boolean, Boolean>>() {
    private var loginResult: Boolean = false
    private var pushResult: Boolean = false

    override fun subscribeActual(observer: SingleObserver<in Pair<Boolean, Boolean>>) {
        val loginResponse = zumpaApp.zumpaSettingsAPI.login(zumpaLoginBody).execute()
        loginResult = loginResponse.code() == 302

        zumpaApp.zumpaPrefs.isLoggedIn = loginResult
        zumpaApp.zumpaPrefs.cookies = if (loginResult) ParseUtils.extractCookies(loginResponse) else null

        try {
            val instanceID = InstanceID.getInstance(zumpaApp)
            val token = instanceID.getToken("542579595500", GoogleCloudMessaging.INSTANCE_ID_SCOPE, null)
            zumpaApp.zumpaPrefs.pushRegId = token
            if (token != null && loginResult) {
                val body = zumpaApp.zumpaSettingsAPI.getMainPageHtml().execute().body().asString()
                val uid = ZumpaSimpleParser.parseUID(body)
                if (uid != null) {
                    val response = zumpaApp.zumpaPHPAPI.register(zumpaLoginBody.nick, uid, token).execute().body().asUTFString()
                    pushResult = "[OK]" == response
                }
            }
        } catch(e: Throwable) {
            e.printStackTrace()
        }
        observer.onSuccess(Pair(loginResult, pushResult))
    }
}


private class LogoutCall(private val zumpaApp: ZumpaReaderApp, private val zumpaUser: String) : Single<Pair<Boolean, Boolean>>() {
    private var logoutResult: Boolean = false
    private var pushResult: Boolean = false

    override fun subscribeActual(observer: SingleObserver<in Pair<Boolean, Boolean>>) {
        try {
            val response = zumpaApp.zumpaPHPAPI.unregister(zumpaUser).execute().body().asUTFString()
            pushResult = "[OK]" == response
        } catch(e: Throwable) {
            e.printStackTrace()
        }
        observer.onSuccess(Pair(true, pushResult))
    }
}