package com.scurab.android.zumpareader.repository

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * The Android half of [AnalyticsReporter]. A translation and nothing else - what the events are
 * called and what their values read as is [AnalyticsEvent]'s business, in `:shared`, so that the
 * desktop, the tests and this all say the same thing.
 *
 * `getInstance` needs a Context, unlike [FirebaseCrashReporter]'s, so it is held rather than
 * resolved per call. Firebase is up before this can be built either way: the google-services plugin
 * merges in a ContentProvider, and those are created before `Application.onCreate`.
 */
class FirebaseAnalyticsReporter(context: Context) : AnalyticsReporter {

    private val analytics = FirebaseAnalytics.getInstance(context)

    override fun log(event: AnalyticsEvent) {
        val params = Bundle().apply {
            event.params.forEach { (key, value) -> putString(key, value) }
        }
        analytics.logEvent(event.name, params)
    }

    override fun setUserProperty(property: AnalyticsUserProperty, value: String) {
        analytics.setUserProperty(property.key, value)
    }
}
