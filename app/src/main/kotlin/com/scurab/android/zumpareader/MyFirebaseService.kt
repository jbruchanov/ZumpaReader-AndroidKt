package com.scurab.android.zumpareader

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.text.Html
import android.util.Log
import android.view.ContextThemeWrapper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.extension.app
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.obtainStyledColor
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.notificationManager

private const val ZUMPA_CHANNEL = "Zumpa"

class MyFirebaseService : FirebaseMessagingService() {

    private val VIBRATE_TEMPLATE = longArrayOf(100L, 600L, 200L, 200L, 200L, 200L)
    private val NOTIFY_ID = 974561

    override fun onMessageReceived(msg: RemoteMessage) {
        Log.i("MyFirebaseService", "Received")
        msg.data.let { n ->
            try {
                onReceiveMessage(n.getValue("subject") ?: "", n.getValue("body"))
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("CheckResult")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        //we need to
        val app = baseContext.app()
        val userName = app.zumpaPrefs.loggedUserName
        if (userName != null) {
            Observable.fromCallable {
                val body = app.zumpaAPI.getMainPageHtml().execute().body()!!.asString()
                app.zumpaPrefs.loggedUserName
                val uid = ZumpaSimpleParser.parseUID(body)
                if (uid != null) {
                    val response = app.zumpaPHPAPI.register(userName, uid, token).execute().body()!!.asUTFString()
                    Log.i("MyFirebaseService", "Result:" + ("[OK]" == response))
                }
            }.subscribeOn(Schedulers.io())
                    .subscribe({
                        //ok we are done, nothing to do here
                    }, {
                        //just ignore it
                        Log.i("MyFirebaseService", "Unable to send newToken to server")
                        it.printStackTrace()
                    })
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nc = NotificationChannel(ZUMPA_CHANNEL, ZUMPA_CHANNEL, NotificationManager.IMPORTANCE_DEFAULT)
                notificationManager.createNotificationChannel(nc)
            }
            ContextThemeWrapper(context, R.style.ThemeBlack)
                    .notificationManager.notify(NOTIFY_ID, notification)
        }
    }

    private val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) R.mipmap.ic_silhouette else R.mipmap.ic_launcher

    private fun onCreateSimpleNotification(context: Context, subject: String, msg: String): Notification {
        return NotificationCompat.Builder(context, ZUMPA_CHANNEL)
                .setSmallIcon(icon)
                .setChannelId(ZUMPA_CHANNEL)
                .setVibrate(VIBRATE_TEMPLATE)
                .setColor(context.obtainStyledColor(R.attr.contextColor))
                .setContentTitle(subject)
                .setContentText(msg)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .build()
    }

    private fun onCreateZumpaNotification(context: Context, msg: String): Notification {
        val pushMsg = ZumpaSimpleParser.parsePushMessage(msg)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_THREAD_ID, pushMsg.threadId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pIntent = PendingIntent.getActivity(context, MainActivity.PUSH_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context, ZUMPA_CHANNEL)
                .setSmallIcon(icon)
                .setChannelId(ZUMPA_CHANNEL)
                .setVibrate(VIBRATE_TEMPLATE)
                .setColor(context.obtainStyledColor(R.attr.contextColor))
                .setContentTitle(context.getString(R.string.notification_header))
                .setContentText(pushMsg.from)
                .setContentIntent(pIntent)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .build()
    }
}

