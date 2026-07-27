package io.github.hiosdra.patches.extension.backgroundplayback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaControllerCompat
import androidx.media.SessionToken2
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommandGroup
import androidx.media3.session.SessionResult
import com.bitmovin.player.api.Player as BitmovinPlayer
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Background Playback Service using Media3 MediaSessionService.
 *
 * Provides foreground service with mediaPlayback type for background audio.
 * Wraps BitmovinPlayer in a Media3 Player interface for system media controls.
 */
@UnstableApi
class BackgroundPlaybackService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "f1tv_background_playback"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "io.github.hiosdra.patches.ACTION_PLAY"
        const val ACTION_PAUSE = "io.github.hiosdra.patches.ACTION_PAUSE"
        const val ACTION_STOP = "io.github.hiosdra.patches.ACTION_STOP"
        const val ACTION_TOGGLE_PIP = "io.github.hiosdra.patches.ACTION_TOGGLE_PIP"
        const val EXTRA_CONTENT_ID = "content_id"
        const val EXTRA_MANIFEST_URL = "manifest_url"
        const val EXTRA_DRM_LICENSE_URL = "drm_license_url"
        const val EXTRA_DRM_HEADERS = "drm_headers"
        const val EXTRA_START_POSITION = "start_position"
        const val EXTRA_IS_LIVE = "is_live"
        const val EXTRA_TITLE = "title"
    }

    private var mediaSession: MediaSession? = null
    private var bitmovinEngine: BitmovinPlaybackEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var notificationManager: NotificationManager? = null
    private var currentContentId: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        Timber.d("BackgroundPlaybackService onCreate")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()

        // Initialize Bitmovin playback engine
        bitmovinEngine = BitmovinPlaybackEngine(this)

        // Create MediaSession with Bitmovin engine as Player
        bitmovinEngine?.let { engine ->
            mediaSession = MediaSession.Builder(this, engine).build()
            setupMediaSessionCallback()
        }

        // Initialize audio focus
        initAudioFocus()

        // Initialize wake lock
        initWakeLock()

        // Start foreground service
        startForeground(NOTIFICATION_ID, buildNotification(), FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun setupMediaSessionCallback() {
        mediaSession?.setCallback(object : MediaSession.Callback() {
            override fun onPlay(controller: MediaController, mediaItem: MediaItem) {
                Timber.d("MediaSession onPlay")
                bitmovinEngine?.play()
            }

            override fun onPause(controller: MediaController) {
                Timber.d("MediaSession onPause")
                bitmovinEngine?.pause()
            }

            override fun onSeekTo(controller: MediaController, position: Long) {
                Timber.d("MediaSession onSeekTo $position")
                bitmovinEngine?.seekTo(position)
            }

            override fun onStop(controller: MediaController) {
                Timber.d("MediaSession onStop")
                stopSelf()
            }

            override fun onSetPlayWhenReady(controller: MediaController, playWhenReady: Boolean) {
                if (playWhenReady) {
                    bitmovinEngine?.play()
                } else {
                    bitmovinEngine?.pause()
                }
            }

            override fun onCustomCommand(
                controller: MediaController,
                customCommand: SessionCommand,
                args: android.os.Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_TOGGLE_PIP) {
                    // Broadcast to app to toggle PiP
                    val intent = Intent(ACTION_TOGGLE_PIP)
                    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    sendBroadcast(intent)
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    )
                }
                return super.onCustomCommand(controller, customCommand, args)
            }
        })
    }

    private fun initAudioFocus() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    mainHandler.post { onAudioFocusChange(focusChange) }
                }
                .setWillPauseWhenDucked(true)
                .build()
        }
    }

    private fun initWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:background-playback"
        ).apply {
            setReferenceCounted(false)
        }
    }

    private fun acquireWakeLock() {
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire()
                Timber.d("Wake lock acquired")
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.d("Wake lock released")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "F1 TV Background Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for F1 TV background audio playback"
                setShowBadge(false)
                sound = null
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val metadata = mediaSession?.controller?.mediaMetadata
        val playbackState = mediaSession?.controller?.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "F1 TV"
        val subtitle = metadata?.getString(MediaMetadata.METADATA_KEY_SUBTITLE) ?: "Formula 1"
        val artwork = metadata?.artworkUri

        // Pending intent to open app
        val openIntent = Intent(this, com.avs.p020f1.p022ui.player.BasePlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Play/Pause action
        val playPauseAction = if (bitmovinEngine?.isPlaying == true) {
            createNotificationAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                ACTION_PAUSE
            )
        } else {
            createNotificationAction(
                android.R.drawable.ic_media_play,
                "Play",
                ACTION_PLAY
            )
        }

        // Stop action
        val stopAction = createNotificationAction(
            android.R.drawable.ic_media_stop,
            "Stop",
            ACTION_STOP
        )

        // PiP action
        val pipAction = createNotificationAction(
            android.R.drawable.ic_menu_fullscreen,
            "Picture-in-Picture",
            ACTION_TOGGLE_PIP
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken!!)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(playPauseAction)
            .addAction(stopAction)
            .addAction(pipAction)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationAction(icon: Int, title: String, action: String): NotificationCompat.Action {
        val intent = Intent(this, BackgroundPlaybackService::class.java).setAction(action)
        val pendingIntent = PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, icon),
            title,
            pendingIntent
        ).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand action: ${intent?.action}")

        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> handlePlayIntent(intent)
                ACTION_PAUSE -> bitmovinEngine?.pause()
                ACTION_STOP -> stopSelf()
                ACTION_TOGGLE_PIP -> { /* Handled by custom command */ }
            }
        }

        return START_STICKY
    }

    private fun handlePlayIntent(intent: Intent) {
        val contentId = intent.getStringExtra(EXTRA_CONTENT_ID)
        val manifestUrl = intent.getStringExtra(EXTRA_MANIFEST_URL)
        val drmLicenseUrl = intent.getStringExtra(EXTRA_DRM_LICENSE_URL)
        val drmHeaders = intent.getStringExtra(EXTRA_DRM_HEADERS)
        val startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0)
        val isLive = intent.getBooleanExtra(EXTRA_IS_LIVE, false)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "F1 TV"

        currentContentId = contentId

        // Request audio focus
        requestAudioFocus()

        // Acquire wake lock
        acquireWakeLock()

        // Load content
        scope.launch {
            bitmovinEngine?.loadContent(
                contentId = contentId ?: "",
                manifestUrl = manifestUrl ?: "",
                drmLicenseUrl = drmLicenseUrl ?: "",
                drmHeadersJson = drmHeaders ?: "{}",
                startPositionMs = startPosition,
                isLive = isLive,
                title = title
            )
        }

        // Update notification
        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange -> onAudioFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    private fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.d("Audio focus gained")
                bitmovinEngine?.play()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.d("Audio focus lost")
                bitmovinEngine?.pause()
                // Stop after 30s if focus not regained
                mainHandler.postDelayed({ stopSelf() }, 30000)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Timber.d("Audio focus transient loss/duck")
                bitmovinEngine?.pause()
            }
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        Timber.d("BackgroundPlaybackService onDestroy")
        scope.cancel()
        releaseWakeLock()
        abandonAudioFocus()
        bitmovinEngine?.release()
        bitmovinEngine = null
        mediaSession?.release()
        mediaSession = null
        stopForeground(true)
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaController.ControllerInfo): MediaSession {
        return mediaSession!!
    }

    /**
     * Update playback info from Activity (e.g., when switching streams)
     */
    fun updatePlaybackInfo(
        contentId: String,
        manifestUrl: String,
        drmLicenseUrl: String,
        drmHeaders: String,
        positionMs: Long,
        isLive: Boolean,
        title: String
    ) {
        currentContentId = contentId

        // Update metadata
        val metadata = MediaMetadata.Builder()
            .setMediaId(contentId)
            .setTitle(title)
            .setSubtitle(if (isLive) "Live" else "On Demand")
            .build()
        mediaSession?.setMediaMetadata(metadata)

        // Update notification
        updateNotification()
    }

    /**
     * Notify position update from Activity
     */
    fun notifyPositionUpdate(positionMs: Long) {
        // MediaSession handles position updates automatically via Player interface
    }

    /**
     * Notify playback error
     */
    fun notifyError(error: Exception) {
        Timber.e(error, "Playback error")
    }

    /**
     * Notify playback completed
     */
    fun notifyCompletion() {
        // MediaSession handles STATE_ENDED via Player interface
    }
}