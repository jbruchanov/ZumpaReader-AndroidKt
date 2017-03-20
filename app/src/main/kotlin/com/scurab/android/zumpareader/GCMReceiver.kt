package com.scurab.android.zumpareader

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.support.v7.app.NotificationCompat
import android.text.Html
import android.view.ContextThemeWrapper
import com.scurab.android.zumpareader.app.MainActivity
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.util.exec
import com.scurab.android.zumpareader.util.obtainStyledColor
import org.jetbrains.anko.notificationManager
import java.net.URLDecoder

/**
 * Created by JBruchanov on 14/01/2016.
 */
class GCMReceiver : BroadcastReceiver() {

    private val RECEIVE = "com.google.android.c2dm.intent.RECEIVE"
    private val VIBRATE_TEMPLATE = longArrayOf(100L, 600L, 200L, 200L, 200L, 200L)
    private val NOTIFY_ID = 974561

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            intent.exec {
                if (RECEIVE == it.action) {
                    val n = onReceiveMessage(ContextThemeWrapper(context, R.style.ThemeBlack), it)
                    context.notificationManager.notify(NOTIFY_ID, n)
                }
            }
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    fun onReceiveMessage(context: Context, intent: Intent): Notification {
        val bundle = intent.extras
        val subject = URLDecoder.decode(bundle.getString("collapse_key"))
        var msg = URLDecoder.decode(bundle.getString("message"))
        if (msg != null) {
            msg = Html.fromHtml(msg).toString()
            msg = Html.fromHtml(msg).toString()//dvojite protoze se to
        }

        var notification: Notification
        when (subject) {
            "ZUMPA" -> notification = onCreateZumpaNotification(context, msg)
            else -> notification = onCreateSimpleNotification(context, subject, msg)
        }
        return notification
    }

    private val icon = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) R.mipmap.ic_silhouette else R.mipmap.ic_launcher

    fun onCreateSimpleNotification(context: Context, subject: String, msg: String): Notification {
        return NotificationCompat.Builder(context)
                .setSmallIcon(icon)
                .setVibrate(VIBRATE_TEMPLATE)
                .setColor(context.obtainStyledColor(R.attr.contextColor))
                .setContentTitle(subject)
                .setContentText(msg)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .build()
    }

    fun onCreateZumpaNotification(context: Context, msg: String): Notification {
        val pushMsg = ZumpaSimpleParser.parsePushMessage(msg)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_THREAD_ID, pushMsg.threadId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pIntent = PendingIntent.getActivity(context, MainActivity.PUSH_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context)
                .setSmallIcon(icon)
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