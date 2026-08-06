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
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import kotlin.coroutines.resume


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
            logoutCall = scope.launch {
                try {
                    logout(zumpaApp, user)
                    isSending = false
                    showLastAuthorPref.isChecked = false
                    showLastAuthorPref.isEnabled = false
                    toast(R.string.done)
                } catch (err: Throwable) {
                    isSending = false
                    toast(err.message)
                }
                crashlyticsPref.title = zumpaApp.zumpaPrefs.userId
            }
        } else {
            toast(R.string.done)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loginCall: Job? = null
    private var logoutCall: Job? = null

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
        loginCall = scope.launch {
            try {
                val (loginResult, pushResult) = login(zumpaApp, ZumpaLoginBody(user, pwd))
                isSending = false
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
            } catch (err: Throwable) {
                isSending = false
                toast(err.message)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isSending = false

        loginCall?.cancel()
        logoutCall?.cancel()

        zumpaApp.zumpaParser.apply {
            userName = zumpaApp.zumpaPrefs.loggedUserName
            isShowLastUser = zumpaApp.zumpaPrefs.showLastAuthor
        }
        zumpaApp.cookieManager.cookieStore.removeAll()
        zumpaApp.cookieManager.put(URI.create(ZR.Constants.ZUMPA_MAIN_URL), zumpaApp.zumpaPrefs.cookiesMap)
        zumpaApp.zumpaParser.isShowLastUser = zumpaApp.zumpaPrefs.showLastAuthor
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/**
 * Logs the user in and registers the push token, [Pair.first] is the login result,
 * [Pair.second] the push registration result.
 */
private suspend fun login(zumpaApp: ZumpaReaderApp, zumpaLoginBody: ZumpaLoginBody): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
    val api = zumpaApp.zumpaOnlineAPI
    val loginResponse = api.login(zumpaLoginBody).execute()
    val loginResult = loginResponse.code() == HttpURLConnection.HTTP_MOVED_TEMP

    zumpaApp.zumpaPrefs.isLoggedIn = loginResult
    zumpaApp.zumpaPrefs.cookies = if (loginResult) ParseUtils.extractCookies(loginResponse) else null

    if (!loginResult) {
        return@withContext Pair(false, false)
    }

    var pushResult = false
    try {
        val token = FirebaseMessaging.getInstance().token.awaitResultOrNull()
        zumpaApp.zumpaPrefs.pushRegId = token
        if (token != null) {
            val body = api.getMainPageHtml().execute().body()!!.asString()
            val uid = ZumpaSimpleParser.parseUID(body)
            if (uid != null) {
                val response = zumpaApp.zumpaPHPAPI.register(zumpaLoginBody.nick, uid, token).execute().body()!!.asUTFString()
                pushResult = "[OK]" == response
            }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    }
    Pair(loginResult, pushResult)
}

private suspend fun logout(zumpaApp: ZumpaReaderApp, zumpaUser: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val response = zumpaApp.zumpaPHPAPI.unregister(zumpaUser).execute().body()!!.asUTFString()
        "[OK]" == response
    } catch (e: Throwable) {
        e.printStackTrace()
        false
    }
}

/**
 * A failed task is not an error here, it only means there is no push token.
 */
private suspend fun <T> Task<T>.awaitResultOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        continuation.resume(if (task.isSuccessful) task.result else null)
    }
}
