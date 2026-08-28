package com.scurab.android.zumpareader.usecase

/**
 * Everything a host does once, at startup, before there is anything on screen.
 *
 * Deliberately a seam with no shared implementation, unlike
 * [com.scurab.android.zumpareader.repository.PushTokenProvider] and friends: the startup work has
 * nothing in common between the two apps. Android creates notification channels and hands the
 * signed-in name to Crashlytics; the desktop app installs the Coil singleton. Neither of those
 * means anything on the other side, and Crashlytics does not exist off Android at all - so the
 * interface is all `:shared` knows, and each host owns its own list of chores.
 *
 * The point is that "what happens at launch" is one named, injectable thing per host rather than a
 * pile of statements in `Application.onCreate` and `main`.
 */
fun interface InitAppUseCase {
    operator fun invoke()
}
