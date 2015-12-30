package com.scurab.android.zumpareader.util

import android.content.Context
import android.content.SharedPreferences
import com.pawegio.kandroid.defaultSharedPreferences
import java.util.*

/**
 * Created by JBruchanov on 29/12/2015.
 */
public class ZumpaPrefs(context: Context) {
    private val KEY_COOKIES = "KEY_COOKIES"
    private val KEY_IS_LOGGED_IN = "KEY_IS_LOGGED_IN"
    private val KEY_LOAD_IMAGES = "KEY_LOAD_IMAGES"
    private val KEY_NICK_NAME = "KEY_NICK_NAME"
    private val KEY_USER_NAME = "prefUser"

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
}