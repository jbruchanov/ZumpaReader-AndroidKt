package com.scurab.android.zumpareader.util

import android.content.Context
import android.content.SharedPreferences
import com.scurab.android.zumpareader.ZR
import org.jetbrains.anko.defaultSharedPreferences
import java.util.*

/**
 * Created by JBruchanov on 29/12/2015.
 */
class ZumpaPrefs(context: Context) {

    companion object {
        val KEY_USER_NAME = "KEY_USER_NAME"
        val KEY_PASSWORD = "KEY_PASSWORD"
        val KEY_LOGIN = "KEY_LOGIN"
        val KEY_SHOW_LAST_AUTHOR = "KEY_SHOW_LAST_AUTHOR"
        val KEY_OFFLINE = "KEY_OFFLINE"
        val KEY_FILTER = "KEY_FILTER"
    }

    private val KEY_COOKIES = "KEY_COOKIES"
    private val KEY_IS_LOGGED_IN = "KEY_IS_LOGGED_IN"
    private val KEY_LOAD_IMAGES = "KEY_LOAD_IMAGES"
    private val KEY_NICK_NAME = "KEY_NICK_NAME"
    private val KEY_READ_STATES = "KEY_READ_STATES"
    private val KEY_LAST_CAMERA_URI = "KEY_LAST_CAMERA_URI"
    private val KEY_PUSH_REG_ID = "KEY_PUSH_REG_ID"

    private val sharedPrefs: SharedPreferences

    init {
        sharedPrefs = context.defaultSharedPreferences
    }

    var cookies: Set<String>?
        get() {
            return sharedPrefs.getStringSet(KEY_COOKIES, null)
        }

        set(value) {
            sharedPrefs.edit().putStringSet(KEY_COOKIES, value).apply()
        }

    val cookiesMap: MutableMap<String, MutableList<String>>
        get() {
            var map: MutableMap<String, MutableList<String>> = HashMap()
            cookies?.let {
                var list: MutableList<String> = ArrayList()
                list.addAll(it)
                if (showLastAuthor) {
                    list.add("%s=1;".format(ZR.Constants.ZUMPA_SHOW_LAST_ANSWER_AUTHOR_KEY))
                }
                map.put("Set-Cookie", list)
            }
            return map
        }

    val isLoggedInNotOffline: Boolean
        get() {
            return isLoggedIn && !isOffline
        }

    var isLoggedIn: Boolean
        get() {
            return sharedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
        }

        set(value) {
            sharedPrefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()
        }

    val loggedUserName: String? get() = if (isLoggedIn) sharedPrefs.getString(KEY_USER_NAME, null) else null

    val loadImages: Boolean
        get() {
            return sharedPrefs.getBoolean(KEY_LOAD_IMAGES, true)
        }

    val nickName: String
        get() {
            val uname = sharedPrefs.getString(KEY_USER_NAME, "")
            val nick = sharedPrefs.getString(KEY_NICK_NAME, uname)
            return if (nick.isEmpty()) uname else nick
        }

    var readStates: String?
        get() {
            return sharedPrefs.getString(KEY_READ_STATES, null)
        }

        set(value) {
            sharedPrefs.edit().putString(KEY_READ_STATES, value).apply()
        }

    var filter: String
        get() {
            return if (isLoggedIn) sharedPrefs.getString(KEY_FILTER, "0") else "0"
        }

        set(value) {
            sharedPrefs.edit().putString(KEY_FILTER, value).apply()
        }

    val showLastAuthor: Boolean
        get() {
            return sharedPrefs.getBoolean(KEY_SHOW_LAST_AUTHOR, false)
        }

    var lastCameraUri: String
        get() {
            return sharedPrefs.getString(KEY_LAST_CAMERA_URI, "")
        }

        set(value) {
            sharedPrefs.edit().putString(KEY_LAST_CAMERA_URI, value).apply()
        }

    var pushRegId: String?
        get() {
            return sharedPrefs.getString(KEY_PUSH_REG_ID, null)
        }

        set(value) {
            sharedPrefs.edit().putString(KEY_PUSH_REG_ID, value).apply()
        }

    var isOffline: Boolean
        get() {
            return sharedPrefs.getBoolean(KEY_OFFLINE, false)
        }

        set(value) {
            sharedPrefs.edit().putBoolean(KEY_OFFLINE, value).apply()
        }
}