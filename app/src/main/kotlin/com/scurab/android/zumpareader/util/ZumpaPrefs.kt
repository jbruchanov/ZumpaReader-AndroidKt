package com.scurab.android.zumpareader.util

import android.content.Context
import android.content.SharedPreferences
import com.pawegio.kandroid.defaultSharedPreferences
import com.scurab.android.zumpareader.ZR
import java.util.*

/**
 * Created by JBruchanov on 29/12/2015.
 */
public class ZumpaPrefs(context: Context) {

    companion object {
        public val KEY_USER_NAME = "KEY_USER_NAME"
        public val KEY_PASSWORD = "KEY_PASSWORD"
        public val KEY_LOGIN = "KEY_LOGIN"
        public val KEY_SHOW_LAST_AUTHOR = "KEY_SHOW_LAST_AUTHOR"
    }
    private val KEY_COOKIES = "KEY_COOKIES"
    private val KEY_IS_LOGGED_IN = "KEY_IS_LOGGED_IN"
    private val KEY_LOAD_IMAGES = "KEY_LOAD_IMAGES"
    private val KEY_NICK_NAME = "KEY_NICK_NAME"
    private val KEY_READ_STATES = "KEY_READ_STATES"
    private val KEY_FILTER = "KEY_FILTER"
    private val KEY_LAST_CAMERA_URI = "KEY_LAST_CAMERA_URI"
    private val KEY_PUSH_REG_ID = "KEY_PUSH_REG_ID"
    private val KEY_OFFLINE = "KEY_OFFLINE"

    private val sharedPrefs: SharedPreferences

    init {
        sharedPrefs = context.defaultSharedPreferences
    }

    public var cookies: Set<String>?
        get() {
            return sharedPrefs.getStringSet(KEY_COOKIES, null)
        }

        set(value) {
            sharedPrefs.edit().putStringSet(KEY_COOKIES, value).apply()
        }

    public val cookiesMap : MutableMap<String, MutableList<String>>
        get() {
            var map: MutableMap<String, MutableList<String>> = HashMap()
            cookies.exec {
                var list: MutableList<String> = ArrayList()
                list.addAll(it)
                if (showLastAuthor) {
                    list.add("%s=1;".format(ZR.Constants.ZUMPA_SHOW_LAST_ANSWER_AUTHOR_KEY))
                }
                map.put("Set-Cookie", list)
            }
            return map
        }

    public var isLoggedIn: Boolean
        get() {
            return sharedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
        }

        set(value) {
            sharedPrefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()
        }

    public val loggedUserName: String? get() = if (isLoggedIn) sharedPrefs.getString(KEY_USER_NAME, null) else null

    public val loadImages: Boolean
        get() {
            return sharedPrefs.getBoolean(KEY_LOAD_IMAGES, true)
        }

    public val nickName:String
        get() {
            return sharedPrefs.getString(KEY_NICK_NAME, sharedPrefs.getString(KEY_USER_NAME, ""))
        }

    public var readStates: String?
        get() {
            return sharedPrefs.getString(KEY_READ_STATES, null)
        }

        set(value) {
            sharedPrefs.edit().putString(KEY_READ_STATES, value).apply()
        }

    public var filter: String
        get() {
            return sharedPrefs.getString(KEY_FILTER, "0")
        }

        set(value) {
            sharedPrefs.edit().putString(KEY_FILTER, value).apply()
        }

    public val showLastAuthor: Boolean
        get() {
            return sharedPrefs.getBoolean(KEY_SHOW_LAST_AUTHOR, false)
        }

    public var lastCameraUri: String
        get() {
            return sharedPrefs.getString(KEY_LAST_CAMERA_URI, "")
        }

        set(value) {
            sharedPrefs.edit().putString(KEY_LAST_CAMERA_URI, value).apply()
        }

    public var pushRegId: String?
        get() {
            return sharedPrefs.getString(KEY_PUSH_REG_ID, null)
        }

        set(value) {
            sharedPrefs.edit().putString(KEY_PUSH_REG_ID, value).apply()
        }

    public var isOffline: Boolean
        get() {
            return sharedPrefs.getBoolean(KEY_OFFLINE, false)
        }

        set(value) {
            sharedPrefs.edit().putBoolean(KEY_OFFLINE, value).apply()
        }
}