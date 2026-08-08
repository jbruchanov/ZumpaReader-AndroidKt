package com.scurab.android.zumpareader.util

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

/**
 * The three timestamp formats the lists render, in one place rather than as `SimpleDateFormat`
 * statics in two screen files.
 *
 * `SimpleDateFormat` took a `Locale.US` it did not need - the patterns are all numeric, so there was
 * nothing locale-specific to decide. `kotlinx-datetime`'s formats are locale-independent by
 * construction, and unlike `SimpleDateFormat` they are also thread-safe.
 *
 * The zone is the device's, which is what the old code got by leaving it unspecified.
 */

/** `dd.MM. HH:mm.ss` - the dot before the seconds is not a typo, it is what the list has shown. */
private val LIST_DATE_FORMAT = LocalDateTime.Format {
    day(); char('.'); monthNumber(); chars(". ")
    hour(); char(':'); minute(); char('.'); second()
}

/** `HH:mm` - used for today's threads. */
private val SHORT_TIME_FORMAT = LocalDateTime.Format {
    hour(); char(':'); minute()
}

/** `HH:mm.ss` - every post inside a thread. */
private val POST_TIME_FORMAT = LocalDateTime.Format {
    hour(); char(':'); minute(); char('.'); second()
}

fun Long.formatThreadListTime(useShortFormat: Boolean): String =
    toLocalDateTime().format(if (useShortFormat) SHORT_TIME_FORMAT else LIST_DATE_FORMAT)

fun Long.formatPostTime(): String = toLocalDateTime().format(POST_TIME_FORMAT)

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
