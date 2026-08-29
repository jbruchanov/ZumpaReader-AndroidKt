package com.scurab.android.zumpareader

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.Html
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.scurab.android.zumpareader.ext.notificationManager
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.repository.AuthRepository
import com.scurab.android.zumpareader.ui.main.MainActivity
import com.scurab.android.zumpareader.util.obtainStyledColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Sound and vibration are the channel's, not the notification's: on api 26+ - which is every
 * install, minSdk being 26 - the channel wins and a `setSound`/`setVibrate` here does nothing. Both
 * used to be set on the builders below and had done nothing for years. See
 * [com.scurab.android.zumpareader.usecase.CreateNotificationChannelsUseCase].
 */
private const val ZUMPA_CHANNEL = AppConfig.NotificationChannel.Notifications

/**
 * Registering the push token is fire and forget, it must not be cancelled when the short-lived
 * service instance goes away.
 */
private val pushRegistrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private const val TAG = "MyFirebaseService"

class MyFirebaseService : FirebaseMessagingService() {

    private val auth: AuthRepository by inject()

    private val NOTIFY_ID = 974561

    override fun onMessageReceived(msg: RemoteMessage) {
        Log.i(TAG, "Received")
        msg.data.let { n ->
            try {
                //indexing, not getValue: getValue throws on a missing key rather than answering
                //null, so the elvis that used to be here could never fire and a push without a
                //`subject` threw into the catch below and vanished without a notification
                onReceiveMessage(n["subject"].orEmpty(), n["body"])
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Firebase refreshed the token, so the forum has to be told the new one.
     *
     * Handed straight to [AuthRepository], which is where the same call for a fresh login lives -
     * this used to be a second copy of it and had drifted: it read the online/offline api switch
     * instead of the online api, so a refresh while offline quietly failed, and it never stored
     * the token it had just registered.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        pushRegistrationScope.launch {
            val isRegistered = auth.onPushTokenRefreshed(token)
            Log.i(TAG, "Refreshed token registered: $isRegistered")
        }
    }

    fun onReceiveMessage(subject: String, message: String?) {
        message?.let { it ->
            val context = ContextThemeWrapper(this, R.style.ThemeBlack)
            var msg = Html.fromHtml(it).toString()
            msg = Html.fromHtml(msg).toString()//dvojite protoze se to

            val notification = when (subject) {
                "ZUMPA" -> onCreateZumpaNotification(context, msg)
                else -> onCreateSimpleNotification(context, subject, msg)
            }

            //the channel is not created here. CreateNotificationChannelsUseCase makes it at
            //startup - and firebase starts the process, so that has always run by the time this
            //has. Re-creating it could not change the importance, which is fixed once a channel
            //exists, but it did overwrite the name: the entry in the system settings went from
            //the localised "Notifications" to the literal channel id on every push received.
            notificationManager.notify(NOTIFY_ID, notification)
        }
    }

    private val icon = R.mipmap.ic_silhouette

    private fun onCreateSimpleNotification(context: Context, subject: String, msg: String): Notification {
        return NotificationCompat.Builder(context, ZUMPA_CHANNEL)
                .setSmallIcon(icon)
                .setChannelId(ZUMPA_CHANNEL)
                .setColor(context.obtainStyledColor(R.attr.contextColor))
                .setContentTitle(subject)
                .setContentText(msg)
                .build()
    }

    private fun onCreateZumpaNotification(context: Context, msg: String): Notification {
        val pushMsg = ZumpaSimpleParser.parsePushMessage(msg)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_THREAD_ID, pushMsg.threadId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pIntent = PendingIntent.getActivity(context, MainActivity.PUSH_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        return NotificationCompat.Builder(context, ZUMPA_CHANNEL)
                .setSmallIcon(icon)
                .setChannelId(ZUMPA_CHANNEL)
                .setColor(context.obtainStyledColor(R.attr.contextColor))
                .setContentTitle(context.getString(R.string.notification_header))
                .setContentText(pushMsg.from)
                .setContentIntent(pIntent)
                .setAutoCancel(true)
                .build()
    }
}

