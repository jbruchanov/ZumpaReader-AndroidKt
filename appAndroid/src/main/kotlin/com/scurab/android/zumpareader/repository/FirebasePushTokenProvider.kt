package com.scurab.android.zumpareader.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The Android half of [PushTokenProvider]. Firebase and Play Services stay in `:app`, which is why
 * [AuthRepository] could move to the shared module.
 */
class FirebasePushTokenProvider : PushTokenProvider {

    /**
     * `getToken` is deprecated in favour of `register`, and deliberately not migrated - see
     * `MyFirebaseService.onNewToken` for the whole of it. The two are mutually exclusive: opting
     * into the new one makes this one throw.
     */
    @Suppress("DEPRECATION")
    override suspend fun token(): String? = FirebaseMessaging.getInstance().token.awaitResultOrNull()
}

/** A failed task is not an error here, it only means there is no push token. */
private suspend fun <T> Task<T>.awaitResultOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        continuation.resume(if (task.isSuccessful) task.result else null)
    }
}
