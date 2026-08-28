package com.scurab.android.zumpareader.model

/**
 * The read/new/updated decoration down the left of a thread row.
 *
 * The order matters: the ordinal is what the `LevelListDrawable` behind the bar was keyed on, so it
 * has to keep matching the old [ZumpaThread] `STATE_*` constants.
 *
 * In `:shared` rather than in a ui package because both apps draw this bar, and the rule for which
 * state a thread is in is the forum's, not a screen's - see [stateFor].
 */
enum class ThreadState { None, New, Updated, Own, ResponseForYou }

/**
 * [ZumpaThread.setStateBasedOnReadValue] as a pure function.
 *
 * The `items < readCount` case has no branch there either - it leaves the previous value alone,
 * which the comment attributes to offline mode - so it answers with [current].
 *
 * @param readCount how many messages of this thread have been seen, or null for never opened.
 * @param userName the signed-in user, so a thread of their own can be told apart. Null when nobody
 * is signed in, which is also what it is when the credentials were never stored.
 */
fun ZumpaThread.stateFor(
    readCount: Int?,
    userName: String?,
    current: ThreadState,
): ThreadState = when {
    hasResponseForYou -> ThreadState.ResponseForYou
    readCount == null -> ThreadState.New
    items == readCount ->
        if (userName != null && userName == author) ThreadState.Own else ThreadState.None

    items > readCount -> ThreadState.Updated
    else -> current
}
