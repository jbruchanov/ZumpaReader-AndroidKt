package com.scurab.android.zumpareader.repository

/**
 * What the app reports about itself, and the vocabulary it reports in.
 *
 * A seam beside [PushTokenProvider] rather than an interface in `:appAndroid` the way
 * [CrashReporter] is: the one event declared here is logged from [AuthRepository], which is shared
 * code, so this has to be somewhere `:shared` can see. Firebase behind it on Android,
 * [NoAnalyticsReporter] on the desktop, a recording fake in a test.
 *
 * The names and the parameter values live here rather than in the implementation on purpose. They
 * are the schema a console query is written against, so a rename is a break - and one file both
 * platforms and the tests read from is what stops any of them drifting from it.
 */
interface AnalyticsReporter {

    fun log(event: AnalyticsEvent)

    /**
     * Sticky, unlike an event: it attaches to everything reported afterwards, which is the whole
     * point of one - it is what the events get segmented by.
     */
    fun setUserProperty(property: AnalyticsUserProperty, value: String)
}

sealed interface AnalyticsEvent {

    val name: String
    val params: Map<String, String>

    /**
     * An attempt to tell the forum where to send this install's pushes.
     *
     * Worth counting because a failure is invisible from inside the app: a token that never arrives
     * looks exactly like a forum with nothing to say. [PushRegistrationOutcome.NoUid] is the one to
     * watch - it is what a registration reading the offline snapshot came out as, silently, for
     * however long that bug was there.
     */
    data class PushRegistration(
        val source: PushRegistrationSource,
        val outcome: PushRegistrationOutcome,
    ) : AnalyticsEvent {
        override val name = "push_registration"
        override val params = mapOf("source" to source.value, "outcome" to outcome.value)
    }
}

/** Whether the token was asked for or handed over. */
enum class PushRegistrationSource(val value: String) {
    Login("login"),
    TokenRefresh("token_refresh"),
}

/** Every way [AuthRepository] can finish a registration, one of which is success. */
enum class PushRegistrationOutcome(val value: String) {
    /** The forum answered `[OK]`. */
    Ok("ok"),

    /** Nobody is signed in, so there is no name to register against. */
    NoUser("no_user"),

    /** The platform had no token to offer - firebase failing on Android, the desktop always. */
    NoToken("no_token"),

    /** No uid on the main page, so the call the forum wants cannot be made. */
    NoUid("no_uid"),

    /** The call went through and the forum answered something other than `[OK]`. */
    Rejected("rejected"),

    /** It threw. No network, most likely. */
    Exception("exception"),
}

enum class AnalyticsUserProperty(val key: String) {
    /**
     * The system-wide notification permission. Set at each launch, so it is as current as the last
     * cold start and no more - a revocation mid-session is not seen until the next one.
     */
    NotificationsEnabled("notifications_enabled"),

    /**
     * The importance of the channel the pushes arrive on, which the user can lower themselves.
     * Reported apart from the permission deliberately: silent-because-blocked and
     * silent-because-quietened are one symptom with two different answers.
     */
    ChannelImportance("channel_importance"),
}

/** An [AnalyticsReporter] for platforms with nothing to report to. */
object NoAnalyticsReporter : AnalyticsReporter {
    override fun log(event: AnalyticsEvent) = Unit
    override fun setUserProperty(property: AnalyticsUserProperty, value: String) = Unit
}
