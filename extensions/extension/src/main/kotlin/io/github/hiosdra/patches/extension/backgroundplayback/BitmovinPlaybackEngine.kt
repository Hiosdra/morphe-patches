package io.github.hiosdra.patches.extension.backgroundplayback

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.bitmovin.player.api.Player as BitmovinPlayer
import com.bitmovin.player.api.PlayerConfig
import com.bitmovin.player.api.PlayerEvent
import com.bitmovin.player.api.PlayerListener
import com.bitmovin.player.api.drm.DrmConfig
import com.bitmovin.player.api.drm.WidevineConfig
import com.bitmovin.player.api.media.audio.AudioTrack
import com.bitmovin.player.api.media.subtitle.SubtitleTrack
import com.bitmovin.player.api.source.Source
import com.bitmovin.player.api.source.SourceBuilder
import com.bitmovin.player.api.source.SourceConfig
import com.bitmovin.player.api.source.SourceOptions
import com.bitmovin.player.api.source.TimelineReferencePoint
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Listener interface for playback state changes
 */
interface PlaybackListener {
    fun onStateChanged(state: Int)
    fun onError(error: Exception)
    fun onMetadataChanged(title: String, subtitle: String, artworkUri: Uri?)
    fun onPositionChanged(positionMs: Long)
}

/**
 * Bitmovin Playback Engine - wraps Bitmovin Player and exposes Media3 Player interface
 *
 * This allows using Bitmovin Player with MediaSessionService for background playback.
 */
@UnstableApi
class BitmovinPlaybackEngine(
    private val context: Context
) : Player {

    private var bitmovinPlayer: BitmovinPlayer? = null
    private val listeners = mutableSetOf<PlaybackListener>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Player state
    private var currentState = Player.STATE_IDLE
    private var currentPositionMs: Long = 0
    private var durationMs: Long = 0
    private var bufferedPositionMs: Long = 0
    private var isBuffering = false
    private var currentPlayWhenReady = false
    private var playbackSpeed = 1.0f
    private var currentMediaItem: MediaItem? = null
    private var mediaMetadata: MediaMetadata? = null
    private var currentSourceConfig: SourceConfig? = null
    private var isLive = false

    // Gson for DRM headers parsing
    private val gson = Gson()

    // Bitmovin event listener
    private val bitmovinListener = object : PlayerListener {
        override fun onEvent(event: PlayerEvent) {
            mainHandler.post {
                when (event) {
                    is PlayerEvent.Play -> {
                        currentPlayWhenReady = true
                        currentState = Player.STATE_PLAYING
                        notifyStateChanged()
                    }
                    is PlayerEvent.Playing -> {
                        currentPlayWhenReady = true
                        currentState = Player.STATE_PLAYING
                        notifyStateChanged()
                    }
                    is PlayerEvent.Paused -> {
                        currentPlayWhenReady = false
                        currentState = Player.STATE_PAUSED
                        notifyStateChanged()
                    }
                    is PlayerEvent.StallStarted -> {
                        isBuffering = true
                        currentState = Player.STATE_BUFFERING
                        notifyStateChanged()
                    }
                    is PlayerEvent.StallEnded -> {
                        isBuffering = false
                        if (currentPlayWhenReady) currentState = Player.STATE_PLAYING
                        notifyStateChanged()
                    }
                    is PlayerEvent.TimeChanged -> {
                        currentPositionMs = (event.time * 1000).toLong()
                        notifyPositionChanged()
                    }
                    is PlayerEvent.DurationChanged -> {
                        durationMs = (event.duration * 1000).toLong()
                        notifyPositionChanged()
                    }
                    is PlayerEvent.PlaybackFinished -> {
                        currentState = Player.STATE_ENDED
                        notifyStateChanged()
                        listeners.forEach { it.onMetadataChanged("", "Playback ended", null) }
                    }
                    is PlayerEvent.Error -> {
                        Timber.e(event.exception, "Bitmovin error: ${event.message}")
                        val error = event.exception ?: Exception(event.message)
                        listeners.forEach { it.onError(error) }
                    }
                    is PlayerEvent.Ready -> {
                        currentState = Player.STATE_READY
                        notifyStateChanged()
                    }
                    is PlayerEvent.SourceLoaded -> {
                        updateMetadataFromSource()
                    }
                    is PlayerEvent.MetadataParsed -> {
                        updateMetadataFromSource()
                    }
                }
            }
        }
    }

    init {
        initPlayer()
    }

    private fun initPlayer() {
        val config = PlayerConfig.Builder(context).build()
        bitmovinPlayer = com.bitmovin.player.PlayerFactory.createPlayer(context, config)
        bitmovinPlayer?.addListener(bitmovinListener)
    }

    /**
     * Load content with DRM configuration
     */
    fun loadContent(
        contentId: String,
        manifestUrl: String,
        drmLicenseUrl: String,
        drmHeadersJson: String,
        startPositionMs: Long,
        isLive: Boolean,
        title: String
    ) {
        this.isLive = isLive

        scope.launch {
            withContext(Dispatchers.Main) {
                val sourceConfig = SourceConfig.fromUrl(manifestUrl)
                sourceConfig.title = title

                // Configure DRM if license URL provided
                if (drmLicenseUrl.isNotEmpty()) {
                    try {
                        val headersMap = gson.fromJson(drmHeadersJson, Map::class.java)
                        val widevineConfig = DrmConfig.Builder(WidevineConfig.UUID)
                            .licenseUrl(drmLicenseUrl)
                            .httpHeaders(headersMap)
                            .build()
                        widevineConfig.setKeepDrmSessionsAlive(true)
                        sourceConfig.drmConfig = widevineConfig
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse DRM headers")
                    }
                }

                // Set start position for VOD or time-shift for live
                if (startPositionMs > 0) {
                    val options = SourceOptions()
                    if (isLive) {
                        options.startOffsetTimelineReference = TimelineReferencePoint.End
                        options.startOffset = startPositionMs / 1000.0 // Convert to seconds
                    } else {
                        options.startOffsetTimelineReference = TimelineReferencePoint.Start
                        options.startOffset = startPositionMs / 1000.0
                    }
                    sourceConfig.options = options
                }

                currentSourceConfig = sourceConfig

                val source = SourceBuilder(sourceConfig).build()
                bitmovinPlayer?.load(source)
                bitmovinPlayer?.play()

                // Update Media3 media item
                currentMediaItem = MediaItem.fromUri(manifestUrl)
                    .buildUpon()
                    .setMediaId(contentId)
                    .build()

                // Update metadata
                mediaMetadata = MediaMetadata.Builder()
                    .setMediaId(contentId)
                    .setTitle(title)
                    .setSubtitle(if (isLive) "Live" else "On Demand")
                    .build()

                notifyStateChanged()
            }
        }
    }

    private fun updateMetadataFromSource() {
        bitmovinPlayer?.source?.let { source ->
            val metadata = MediaMetadata.Builder()
                .setMediaId(currentMediaItem?.mediaId ?: "")
                .setTitle(source.config.title ?: "F1 TV")
                .setSubtitle(if (isLive) "Live" else "On Demand")
                .build()
            mediaMetadata = metadata
            listeners.forEach { it.onMetadataChanged(metadata.title ?: "", metadata.subtitle ?: "", null) }
        }
    }

    // Listener management
    fun addListener(listener: PlaybackListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    private fun notifyStateChanged() {
        listeners.forEach { it.onStateChanged(currentState) }
    }

    private fun notifyPositionChanged() {
        listeners.forEach { it.onPositionChanged(currentPositionMs) }
    }

    // ===== Media3 Player Interface Implementation =====

    override val commands: Set<Command>
        get() = setOf(
            Command.PLAY, Command.PAUSE, Command.STOP, Command.SEEK_TO,
            Command.SET_PLAY_WHEN_READY, Command.SET_REPEAT_MODE,
            Command.SET_SHUFFLE_MODE_ENABLED, Command.SET_PLAYBACK_SPEED
        )

    override val playWhenReady: Boolean
        get() = currentPlayWhenReady

    override val playbackState: Int
        get() = currentState

    override val currentPosition: Long
        get() = currentPositionMs

    override val bufferedPosition: Long
        get() = bufferedPositionMs

    override val duration: Long
        get() = if (isLive) TIME_UNSET else durationMs

    override val currentMediaItem: MediaItem?
        get() = currentMediaItem

    override val mediaMetadata: MediaMetadata?
        get() = mediaMetadata

    override val playbackSpeed: Float
        get() = playbackSpeed

    override val isPlaying: Boolean
        get() = currentPlayWhenReady && currentState == Player.STATE_PLAYING

    override val isLoading: Boolean
        get() = currentState == Player.STATE_BUFFERING

    override val seekBackIncrement: Long
        get() = DEFAULT_SEEK_BACK_INCREMENT

    override val seekForwardIncrement: Long
        get() = DEFAULT_SEEK_FORWARD_INCREMENT

    override val availableCommands: Player.Commands
        get() = Player.Commands.Builder().build()

    override val repeatMode: Int
        get() = Player.REPEAT_MODE_OFF

    override val shuffleModeEnabled: Boolean
        get() = false

    override val timeline: Timeline
        get() = Timeline.EMPTY

    override val tracks: Tracks
        get() = Tracks.EMPTY

    override val currentTrackGroups: Any?
        get() = null

    override val currentTrackSelections: Any?
        get() = null

    // Playback control
    override fun play() {
        currentPlayWhenReady = true
        bitmovinPlayer?.play()
    }

    override fun pause() {
        currentPlayWhenReady = false
        bitmovinPlayer?.pause()
    }

    override fun stop() {
        currentPlayWhenReady = false
        bitmovinPlayer?.stop()
        currentState = Player.STATE_IDLE
        notifyStateChanged()
    }

    override fun seekTo(position: Long) {
        bitmovinPlayer?.seek(position / 1000.0) // Bitmovin uses seconds
    }

    override fun seekTo(windowIndex: Int, position: Long) {
        seekTo(position)
    }

    override fun prepare() {
        // Already prepared when loading source
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) play() else pause()
    }

    override fun setPlaybackSpeed(playbackSpeed: Float) {
        this.playbackSpeed = playbackSpeed
        bitmovinPlayer?.setPlaybackSpeed(playbackSpeed)
    }

    override fun setRepeatMode(repeatMode: Int) {
        // Not supported for live streams
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        // Not supported
    }

    override fun addListener(listener: Player.Listener) {
        // Not used - we use our own listener interface
    }

    override fun removeListener(listener: Player.Listener) {
        // Not used
    }

    override fun release() {
        scope.cancel()
        bitmovinPlayer?.removeListener(bitmovinListener)
        bitmovinPlayer?.release()
        bitmovinPlayer = null
        listeners.clear()
    }

    // Additional helper methods
    fun setVolume(volume: Float) {
        bitmovinPlayer?.volume = volume
    }

    fun getAvailableAudioTracks(): List<AudioTrack> {
        return bitmovinPlayer?.source?.availableAudioTracks ?: emptyList()
    }

    fun getAvailableSubtitleTracks(): List<SubtitleTrack> {
        return bitmovinPlayer?.source?.availableSubtitleTracks ?: emptyList()
    }

    fun selectAudioTrack(trackId: String): Boolean {
        return bitmovinPlayer?.setAudioTrack(trackId) == true
    }

    fun selectSubtitleTrack(trackId: String?): Boolean {
        return if (trackId == null) {
            bitmovinPlayer?.setSubtitleTrack(null) == true
        } else {
            bitmovinPlayer?.setSubtitleTrack(trackId) == true
        }
    }
}