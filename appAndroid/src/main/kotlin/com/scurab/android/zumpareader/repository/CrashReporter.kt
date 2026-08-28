package com.scurab.android.zumpareader.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Who the crash reports belong to.
 *
 * Android only, and the interface lives here rather than in `:shared` for that reason - Crashlytics
 * is a Firebase library and there is no desktop equivalent to write a second implementation of. It
 * is an interface at all so [com.scurab.android.zumpareader.usecase.AndroidInitAppUseCase] can be
 * tested without a Firebase app behind it.
 */
interface CrashReporter {
    /** See [com.scurab.android.zumpareader.util.ZumpaPrefs.userId] for what goes in here. */
    fun setUserId(userId: String)
}

/**
 * The real one. `getInstance()` per call rather than once: Crashlytics is a singleton either way,
 * and this is called about as often as somebody signs in.
 *
 * The id is what the settings screen shows under "Crashlytics" and copies to the clipboard, so a
 * user reporting a crash can hand over the same string the console filters on.
 */
class FirebaseCrashReporter : CrashReporter {

    override fun setUserId(userId: String) {
        FirebaseCrashlytics.getInstance().setUserId(userId)
    }
}
