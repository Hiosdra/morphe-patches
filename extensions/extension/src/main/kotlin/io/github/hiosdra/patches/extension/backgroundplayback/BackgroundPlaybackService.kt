package io.github.hiosdra.patches.extension.backgroundplayback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * Keeps the patched F1 TV process in the foreground while its player is
 * continuing after the activity has gone behind the launcher or the screen is
 * off. The host app's existing Bitmovin player remains the audio source.
 */
class BackgroundPlaybackService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("F1 TV playback")
                .setContentText("Playback is continuing in the background")
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOngoing(true)
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "F1 TV background playback",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private companion object {
        const val CHANNEL_ID = "f1tv_background_playback"
        const val NOTIFICATION_ID = 1001
    }
}
