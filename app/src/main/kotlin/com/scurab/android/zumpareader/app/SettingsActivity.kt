package com.scurab.android.zumpareader.app

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceActivity
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.scurab.android.zumpareader.AppConfig
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.component.NotificationStateProvider
import com.scurab.android.zumpareader.content.SendingFragment
import com.scurab.android.zumpareader.ext.toast
import com.scurab.android.zumpareader.model.ZumpaLoginBody
import com.scurab.android.zumpareader.preferences.ButtonPreference
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.ui.applySystemBarsAsPadding
import com.scurab.android.zumpareader.util.ParseUtils
import com.scurab.android.zumpareader.util.ZumpaPrefs
import com.scurab.android.zumpareader.util.saveToClipboard
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.net.HttpURLConnection
import java.net.URI


/**
 * Created by JBruchanov on 29/12/2015.
 */
class SettingsActivity : PreferenceActivity(), SendingFragment {

    override fun requireContext(): Context = this
    private val buttonPref by lazy { findPreference(ZumpaPrefs.KEY_LOGIN) }
    private val permissionsPref by lazy { findPreference(ZumpaPrefs.KEY_NOTIFICATIONS) as ButtonPreference }
    private val showLastAuthorPref by lazy { findPreference(ZumpaPrefs.KEY_SHOW_LAST_AUTHOR) as CheckBoxPreference }
    private val filterPref by lazy { findPreference(ZumpaPrefs.KEY_FILTER) }
    private val crashlyticsPref by lazy { findPreference(ZumpaPrefs.KEY_CRASHYLYTICS) }
    private val notificationStateProvider by lazy { NotificationStateProvider(this) }

    val zumpaApp: ZumpaReaderApp
        get() {
            return application as ZumpaReaderApp
        }

    private var progressDialog: ProgressDialog? = null
    override var sendingDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.settings)
        findViewById<View>(android.R.id.content).applySystemBarsAsPadding()

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
        showLastAuthorPref.isEnabled = zumpaApp.zumpaPrefs.isLoggedIn

        permissionsPref.setOnPreferenceClickListener {
            val permsEnabled = notificationStateProvider.areNotificationsEnabled(AppConfig.NotificationChannel.Notifications)
            if (permsEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${packageName}")))
            } else {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 123)
            }
            true
        }

        crashlyticsPref.title = zumpaApp.zumpaPrefs.userId
        crashlyticsPref.setOnPreferenceClickListener {
            val userId = zumpaApp.zumpaPrefs.userId
            saveToClipboard(userId)
            toast("'${userId}' saved to clipboard")
            true
        }
    }

    override fun onResume() {
        super.onResume()
        val permsEnabled = notificationStateProvider.areNotificationsEnabled(AppConfig.NotificationChannel.Notifications)
        permissionsPref.summary = getString(if (permsEnabled) R.string.enabled else R.string.disabled)
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
                        showLastAuthorPref.isChecked = false
                        showLastAuthorPref.isEnabled = false
                        toast(R.string.done)
                    } else {
                        toast(err.message)
                    }
                    crashlyticsPref.title = zumpaApp.zumpaPrefs.userId
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
                        showLastAuthorPref.isEnabled = true
                        buttonPref.title = resources.getString(R.string.logout)
                    }
                    crashlyticsPref.title = zumpaApp.zumpaPrefs.userId
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

        zumpaApp.zumpaParser.apply {
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

    override fun subscribeActual(observer: SingleObserver<in Pair<Boolean, Boolean>>) {
        val api = zumpaApp.zumpaOnlineAPI
        val loginResponse = api.login(zumpaLoginBody).execute()
        loginResult = loginResponse.code() == HttpURLConnection.HTTP_MOVED_TEMP

        zumpaApp.zumpaPrefs.isLoggedIn = loginResult
        zumpaApp.zumpaPrefs.cookies = if (loginResult) ParseUtils.extractCookies(loginResponse) else null

        if (!loginResult) {
            observer.onSuccess(Pair(loginResult, false))
            return
        }

        try {
            FirebaseMessaging.getInstance()
                .token
                .addOnCompleteListener { task ->
                    Observable
                        .fromCallable {
                            var pushResult = false
                            if (task.isSuccessful) {
                                // Get new Instance ID token
                                val token = task.result
                                zumpaApp.zumpaPrefs.pushRegId = token
                                if (token != null && loginResult) {
                                    val body = api.getMainPageHtml().execute().body()!!.asString()
                                    val uid = ZumpaSimpleParser.parseUID(body)
                                    if (uid != null) {
                                        val response = zumpaApp.zumpaPHPAPI.register(zumpaLoginBody.nick, uid, token).execute().body()!!.asUTFString()
                                        pushResult = "[OK]" == response
                                    }
                                }
                            }
                            Pair(loginResult, pushResult)
                        }
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            { result -> observer.onSuccess(result) },
                            { err -> observer.onError(err) }
                        )
                }
        } catch (e: Throwable) {
            e.printStackTrace()
            observer.onSuccess(Pair(loginResult, false))
        }
    }
}


private class LogoutCall(private val zumpaApp: ZumpaReaderApp, private val zumpaUser: String) : Single<Pair<Boolean, Boolean>>() {
    private var logoutResult: Boolean = false
    private var pushResult: Boolean = false

    override fun subscribeActual(observer: SingleObserver<in Pair<Boolean, Boolean>>) {
        try {
            val response = zumpaApp.zumpaPHPAPI.unregister(zumpaUser).execute().body()!!.asUTFString()
            pushResult = "[OK]" == response
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        observer.onSuccess(Pair(true, pushResult))
    }
}
