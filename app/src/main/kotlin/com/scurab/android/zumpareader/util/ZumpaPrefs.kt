package com.scurab.android.zumpareader.util

import android.content.Context
import android.content.SharedPreferences
import com.pawegio.kandroid.defaultSharedPreferences

/**
 * Created by JBruchanov on 29/12/2015.
 */
public class ZumpaPrefs(context: Context) {
    private val KEY_PHPSESSION_ID = "KEY_PHPSESSION_ID"
    private val KEY_IS_LOGGED_IN = "KEY_IS_LOGGED_IN"
    private val KEY_LOAD_IMAGES = "KEY_LOAD_IMAGES"

    private val sharedPrefs: SharedPreferences

    init {
        sharedPrefs = context.defaultSharedPreferences
    }

    public var phpSessionId: String?
        get() {
            return sharedPrefs.getString(KEY_PHPSESSION_ID, null)
        }

        set(value) {
            sharedPrefs.edit().putString(KEY_PHPSESSION_ID, value).apply()
        }

    public var isLoggedIn: Boolean
        get() {
            return sharedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
        }

        set(value) {
            sharedPrefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()
        }

    public val loadImages: Boolean
        get() {
            return sharedPrefs.getBoolean(KEY_LOAD_IMAGES, false)
        }
}