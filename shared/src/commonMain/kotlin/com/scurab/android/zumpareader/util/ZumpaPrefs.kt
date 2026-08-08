package com.scurab.android.zumpareader.util

/**
 * Created by JBruchanov on 29/12/2015.
 *
 * Unchanged except for what it sits on: a [KeyValueStore] rather than `SharedPreferences` directly,
 * which is the whole reason this and the repositories that read it can be common code. The keys and
 * the defaults are the same, so an existing install keeps its settings.
 */
class ZumpaPrefs(private val store: KeyValueStore) {

    companion object {
        val KEY_USER_NAME = "KEY_USER_NAME"
        val KEY_PASSWORD = "KEY_PASSWORD"
        val KEY_LOGIN = "KEY_LOGIN"
        val KEY_SHOW_LAST_AUTHOR = "KEY_SHOW_LAST_AUTHOR"
        val KEY_OFFLINE = "KEY_OFFLINE"
        val KEY_FILTER = "KEY_FILTER"
        val KEY_NOTIFICATIONS = "KEY_NOTIFICATIONS"
        val KEY_CRASHYLYTICS = "KEY_CRASHYLYTICS"

        //observed by ZumpaSettingsRepository, which needs the key a write lands on
        val KEY_IS_LOGGED_IN = "KEY_IS_LOGGED_IN"
        val KEY_LOAD_IMAGES = "KEY_LOAD_IMAGES"
        val KEY_NICK_NAME = "KEY_NICK_NAME"
    }

    private val KEY_COOKIES = "KEY_COOKIES"
    private val KEY_READ_STATES = "KEY_READ_STATES"
    private val KEY_PUSH_REG_ID = "KEY_PUSH_REG_ID"
    private val KEY_USER_ID = "KEY_USER_ID"

    /** For [com.scurab.android.zumpareader.repository.ZumpaSettingsRepository] to observe writes. */
    val changes get() = store.changes

    var cookies: Set<String>?
        get() = store.getStringSet(KEY_COOKIES)
        set(value) = store.putStringSet(KEY_COOKIES, value)

    val isLoggedInNotOffline: Boolean
        get() = isLoggedIn && !isOffline

    var isLoggedIn: Boolean
        get() = store.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = store.putBoolean(KEY_IS_LOGGED_IN, value)

    val loggedUserName: String? get() = if (isLoggedIn) store.getString(KEY_USER_NAME, null) else null

    val userName: String get() = store.getString(KEY_USER_NAME, "") ?: ""
    fun setUserName(value: String) = store.putString(KEY_USER_NAME, value)

    val password: String get() = store.getString(KEY_PASSWORD, "") ?: ""
    fun setPassword(value: String) = store.putString(KEY_PASSWORD, value)

    fun setNick(value: String) = store.putString(KEY_NICK_NAME, value)

    fun setLoadImages(value: Boolean) = store.putBoolean(KEY_LOAD_IMAGES, value)

    fun setShowLastAuthor(value: Boolean) = store.putBoolean(KEY_SHOW_LAST_AUTHOR, value)

    val loadImages: Boolean
        get() = store.getBoolean(KEY_LOAD_IMAGES, true)

    val nickName: String
        get() {
            val uname = store.getString(KEY_USER_NAME, "") ?: ""
            val nick = store.getString(KEY_NICK_NAME, uname) ?: uname
            return nick.ifEmpty { uname }
        }

    var readStates: String?
        get() = store.getString(KEY_READ_STATES, null)
        set(value) = store.putString(KEY_READ_STATES, value)

    var filter: String
        get() = if (isLoggedIn) store.getString(KEY_FILTER, "0") ?: "0" else "0"
        set(value) = store.putString(KEY_FILTER, value)

    val showLastAuthor: Boolean
        get() = store.getBoolean(KEY_SHOW_LAST_AUTHOR, false)

    var pushRegId: String?
        get() = store.getString(KEY_PUSH_REG_ID, null)
        set(value) = store.putString(KEY_PUSH_REG_ID, value)

    var isOffline: Boolean
        get() = store.getBoolean(KEY_OFFLINE, false)
        set(value) = store.putBoolean(KEY_OFFLINE, value)

    var userId: String?
        get() = loggedUserName?.takeIf { it.isNotEmpty() } ?: store.getString(KEY_USER_ID, null)
        set(value) = store.putString(KEY_USER_ID, value)
}
