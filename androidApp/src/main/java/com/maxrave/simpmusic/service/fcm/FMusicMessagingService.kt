package com.maxrave.simpmusic.service.fcm

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.BuildConfig
import com.maxrave.simpmusic.MainActivity
import com.maxrave.simpmusic.R

object FMusicPush {
    const val CHANNEL_ID = "fmusic_announcements"
    const val TOPIC_ALL = "all"
    private const val TAG = "FMusicPush"

    fun init(context: Context) {
        runCatching {
            createChannel(context)
            val messaging = FirebaseMessaging.getInstance()
            messaging.subscribeToTopic(TOPIC_ALL)
            if (BuildConfig.DEBUG) {
                messaging.token.addOnSuccessListener { Logger.w(TAG, "FCM token: $it") }
            }
        }
    }

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "F Music Announcements",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Updates and announcements from F Music"
                }
            manager.createNotificationChannel(channel)
        }
    }
}

class FMusicMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (BuildConfig.DEBUG) Logger.w("FMusicPush", "New FCM token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        show(
            title = title?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name),
            body = body,
            url = message.data["url"],
        )
    }

    @SuppressLint("MissingPermission")
    private fun show(
        title: String,
        body: String?,
        url: String?,
    ) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        FMusicPush.createChannel(this)

        val link = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        val intent =
            if (link != null) {
                Intent(Intent.ACTION_VIEW, link.toUri())
            } else {
                Intent(this, MainActivity::class.java)
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val id = System.currentTimeMillis().toInt()
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                id,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(this, FMusicPush.CHANNEL_ID)
                .setSmallIcon(R.drawable.mono)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        manager.notify(id, notification)
    }
}
