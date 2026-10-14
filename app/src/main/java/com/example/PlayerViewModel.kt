package com.example

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.api.NetworkModule
import com.example.domain.SearchResult
import com.example.domain.StreamInfo
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayableItem(
    val id: String, // videoId for online, or path for local
    val title: String,
    val artist: String,
    val url: String, // resolved stream URL or local file path
    val thumbnailUrl: String,
    val isLocal: Boolean,
    val durationSeconds: Long = 0L,
    val viewCountText: String = ""
)

class PlayerViewModel : ViewModel() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _player = MutableStateFlow<Player?>(null)
    val player = _player.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isResolvingStream = MutableStateFlow(false)
    val isResolvingStream = _isResolvingStream.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError = _playbackError.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem = _currentMediaItem.asStateFlow()

    // Real-time playback status flows
    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration = _playbackDuration.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress = _playbackProgress.asStateFlow()

    // Fullscreen player sheet visible state
    private val _showFullPlayer = MutableStateFlow(false)
    val showFullPlayer = _showFullPlayer.asStateFlow()

    // Playback Queue state management
    private val _playQueue = MutableStateFlow<List<PlayableItem>>(emptyList())
    val playQueue = _playQueue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex = _currentIndex.asStateFlow()

    private val getPlaybackStreamUseCase = NetworkModule.getPlaybackStreamUseCase
    private var streamResolveJob: Job? = null
    private var streamResolveGeneration = 0L
    private var pendingMediaItem: MediaItem? = null
    private var orientationBeforePlayer: Int? = null

    init {
        // Continuous position polling flow running in VM scope
        viewModelScope.launch {
            while (true) {
                try {
                    _player.value?.let { p ->
                        _isPlaying.value = p.isPlaying
                        _isBuffering.value = p.playbackState == Player.STATE_BUFFERING
                        _playbackPosition.value = p.currentPosition
                        val dur = p.duration
                        if (dur > 0) {
                            _playbackDuration.value = dur
                            _playbackProgress.value = p.currentPosition.toFloat() / dur
                        }
                    }
                } catch (_: Exception) {
                    // Ignore background binder errors during active session transitions.
                }
                delay(400)
            }
        }
    }

    fun setShowFullPlayer(show: Boolean) {
        _showFullPlayer.value = show
        if (!show && _isResolvingStream.value) {
            streamResolveGeneration++
            streamResolveJob?.cancel()
            streamResolveJob = null
            _isResolvingStream.value = false
        }
    }

    fun toggleVideoOrientation(activity: Activity, currentlyLandscape: Boolean) {
        if (orientationBeforePlayer == null) {
            orientationBeforePlayer = activity.requestedOrientation
        }
        activity.requestedOrientation = if (currentlyLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun restoreVideoOrientation(activity: Activity) {
        orientationBeforePlayer?.let { activity.requestedOrientation = it }
        orientationBeforePlayer = null
    }

    fun initController(context: Context) {
        if (controllerFuture != null) return

        val isUnderTest = try {
            Class.forName("org.robolectric.Robolectric") != null
        } catch (_: Exception) {
            false
        }
        if (isUnderTest) return

        try {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    val controller = controllerFuture?.get() ?: return@addListener
                    _player.value = controller
                    _isPlaying.value = controller.isPlaying
                    _isBuffering.value = controller.playbackState == Player.STATE_BUFFERING
                    _currentMediaItem.value = controller.currentMediaItem
                    controller.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            _isBuffering.value = playbackState == Player.STATE_BUFFERING
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            _isBuffering.value = false
                            _playbackError.value = error.localizedMessage ?: "تعذر تشغيل هذا المقطع."
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            _currentMediaItem.value = mediaItem
                            _playbackPosition.value = controller.currentPosition
                            val dur = controller.duration
                            _playbackDuration.value = if (dur > 0) dur else 0L
                            _playbackProgress.value = if (dur > 0) {
                                controller.currentPosition.toFloat() / dur
                            } else {
                                0f
                            }
                        }
                    })
                    pendingMediaItem?.let { pending ->
                        pendingMediaItem = null
                        startMediaItem(pending)
                    }
                } catch (e: Exception) {
                    Log.e("PlayerViewModel", "Failed to get controller", e)
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Failed to initialize media controller", e)
        }
    }

    fun playFromSearchResult(result: SearchResult) {
        val item = PlayableItem(
            id = result.videoId,
            title = result.title,
            artist = result.uploaderName,
            url = "",
            thumbnailUrl = result.thumbnailUrl,
            isLocal = false,
            durationSeconds = result.durationSeconds
        )
        playQueueItem(item, listOf(item), 0)
    }

    fun playStream(info: StreamInfo) {
        _playbackError.value = null
        _isResolvingStream.value = false
        play(
            url = info.primaryPlayUrl,
            title = info.title,
            artist = info.uploader,
            thumbnail = info.thumbnailUrl
        )
    }

    fun playLocalFile(uri: Uri, title: String) {
        _playbackError.value = null
        _isResolvingStream.value = false
        play(url = uri.toString(), title = title, artist = "تنزيلات Cove", thumbnail = "")
    }

    fun playQueueItem(item: PlayableItem, queue: List<PlayableItem>, index: Int) {
        streamResolveGeneration++
        val requestGeneration = streamResolveGeneration
        streamResolveJob?.cancel()
        streamResolveJob = null

        val updatedQueue = queue.toMutableList()
        if (index in updatedQueue.indices) updatedQueue[index] = item
        _playQueue.value = updatedQueue
        _currentIndex.value = index
        _showFullPlayer.value = true
        _playbackError.value = null
        _isBuffering.value = false

        if (item.isLocal || item.url.isNotBlank()) {
            _isResolvingStream.value = false
            play(url = item.url, title = item.title, artist = item.artist, thumbnail = item.thumbnailUrl)
            return
        }

        // Open the player immediately and resolve one time in the background.
        _isResolvingStream.value = true
        streamResolveJob = viewModelScope.launch {
            try {
                val info = getPlaybackStreamUseCase(item.id)
                if (requestGeneration != streamResolveGeneration) return@launch
                if (info == null || info.primaryPlayUrl.isBlank()) {
                    _playbackError.value = "تعذر الحصول على رابط التشغيل. حاول مرة أخرى."
                    return@launch
                }

                val resolvedItem = item.copy(
                    title = info.title.ifBlank { item.title },
                    artist = info.uploader.ifBlank { item.artist },
                    url = info.primaryPlayUrl,
                    thumbnailUrl = info.thumbnailUrl.ifBlank { item.thumbnailUrl },
                    durationSeconds = info.durationSeconds.takeIf { it > 0L } ?: item.durationSeconds
                )
                val currentQueue = _playQueue.value.toMutableList()
                if (index in currentQueue.indices) {
                    currentQueue[index] = resolvedItem
                    _playQueue.value = currentQueue
                }
                _playbackError.value = null
                play(
                    url = resolvedItem.url,
                    title = resolvedItem.title,
                    artist = resolvedItem.artist,
                    thumbnail = resolvedItem.thumbnailUrl
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestGeneration == streamResolveGeneration) {
                    _playbackError.value = e.localizedMessage ?: "تعذر تجهيز البث. حاول مرة أخرى."
                    Log.e("PlayerViewModel", "Failed to resolve playback stream", e)
                }
            } finally {
                if (requestGeneration == streamResolveGeneration) {
                    _isResolvingStream.value = false
                    streamResolveJob = null
                }
            }
        }
    }

    fun retryCurrentQueueItem() {
        val index = _currentIndex.value
        val queue = _playQueue.value
        if (index !in queue.indices) return
        val item = queue[index].copy(url = "")
        val retryQueue = queue.toMutableList().apply { this[index] = item }
        playQueueItem(item, retryQueue, index)
    }

    fun playNext() {
        val queue = _playQueue.value
        val index = _currentIndex.value
        if (queue.isNotEmpty() && index < queue.size - 1) {
            playQueueItem(queue[index + 1], queue, index + 1)
        }
    }

    fun playPrevious() {
        val queue = _playQueue.value
        val index = _currentIndex.value
        if (queue.isNotEmpty() && index > 0) {
            playQueueItem(queue[index - 1], queue, index - 1)
        }
    }

    fun seekTo(positionMs: Long) {
        _player.value?.let { p ->
            p.seekTo(positionMs)
            _playbackPosition.value = positionMs
            val dur = _playbackDuration.value
            _playbackProgress.value = if (dur > 0) positionMs.toFloat() / dur else 0f
        }
    }

    private fun play(url: String, title: String, artist: String, thumbnail: String) {
        if (url.isBlank()) {
            _playbackError.value = "رابط التشغيل غير صالح."
            return
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(url)
            .setUri(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(android.net.Uri.parse(thumbnail))
                    .build()
            )
            .build()

        _currentMediaItem.value = mediaItem
        _playbackPosition.value = 0L
        _playbackDuration.value = 0L
        _playbackProgress.value = 0f
        _playbackError.value = null
        _isBuffering.value = true
        startMediaItem(mediaItem)
    }

    private fun startMediaItem(mediaItem: MediaItem) {
        val activePlayer = _player.value
        if (activePlayer == null) {
            pendingMediaItem = mediaItem
            return
        }
        try {
            activePlayer.setMediaItem(mediaItem)
            activePlayer.prepare()
            activePlayer.play()
        } catch (e: Exception) {
            _isBuffering.value = false
            _playbackError.value = e.localizedMessage ?: "تعذر بدء تشغيل هذا المقطع."
            Log.e("PlayerViewModel", "Failed to start media item", e)
        }
    }

    override fun onCleared() {
        streamResolveJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
