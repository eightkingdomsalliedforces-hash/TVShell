package dev.tvshell.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import dev.tvshell.shared.anime.AnimeStreamCandidate
import dev.tvshell.shared.anime.AnimePlaybackSnapshot
import dev.tvshell.shared.anime.DesktopMediaProxy
import java.awt.BorderLayout
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import javax.swing.JPanel

@Composable
actual fun PlatformAnimeVideoSurface(
    candidate: AnimeStreamCandidate?,
    signal: WebRuntimeSignal,
    onExitRequested: () -> Unit,
    modifier: Modifier,
) {
    val holder = remember { DesktopAnimeMediaHolder() }
    SwingPanel(factory = { holder.container }, modifier = modifier)
    LaunchedEffect(candidate?.url) { candidate?.let(holder::load) }
    LaunchedEffect(signal.sequence) {
        if (signal.sequence > 0) holder.dispatch(signal.command)
    }
    DisposableEffect(Unit) { onDispose(holder::dispose) }
}

internal object DesktopAnimePlaybackRegistry {
    private var owner: Any? = null
    private var provider: (() -> AnimePlaybackSnapshot)? = null

    @Synchronized
    fun register(owner: Any, provider: () -> AnimePlaybackSnapshot) {
        this.owner = owner
        this.provider = provider
    }

    @Synchronized
    fun unregister(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            provider = null
        }
    }

    fun snapshot(): AnimePlaybackSnapshot = synchronized(this) { provider }
        ?.invoke()
        ?: AnimePlaybackSnapshot()
}

private class DesktopAnimeMediaHolder {
    @Volatile private var playbackPositionSeconds = 0.0
    @Volatile private var playbackDurationSeconds = 0.0
    @Volatile private var playbackIsPlaying = false
    @Volatile private var playbackError: String? = null
    private val libVLC = DesktopLibVLCPlayer.createOrNull { reason ->
        playbackError = "Windows 內建 LibVLC 初始化失敗：$reason"
    }
    private val fxPanel = if (libVLC == null) JFXPanel() else null
    val container = libVLC?.container ?: JPanel(BorderLayout()).apply { add(requireNotNull(fxPanel), BorderLayout.CENTER) }
    private var player: MediaPlayer? = null
    private var mediaView: MediaView? = null
    private val registryOwner = Any()

    init {
        DesktopAnimePlaybackRegistry.register(registryOwner, ::snapshot)
        if (libVLC == null) {
            Platform.setImplicitExit(false)
            Platform.runLater {
                val view = MediaView().apply { isPreserveRatio = true }
                val root = Group(view)
                val scene = Scene(root)
                view.fitWidthProperty().bind(scene.widthProperty())
                view.fitHeightProperty().bind(scene.heightProperty())
                mediaView = view
                requireNotNull(fxPanel).scene = scene
            }
        }
    }

    fun load(candidate: AnimeStreamCandidate) {
        playbackPositionSeconds = 0.0
        playbackDurationSeconds = 0.0
        playbackIsPlaying = false
        playbackError = null
        libVLC?.let {
            val playbackCandidate = if (
                candidate.headers["resolver"] == "torrent-stream" &&
                (candidate.url.startsWith("http://127.0.0.1:") || candidate.url.startsWith("http://localhost:"))
            ) candidate else candidate.copy(
                url = DesktopMediaProxy.playbackURL(candidate),
                headers = emptyMap(),
            )
            it.load(playbackCandidate)
            return
        }
        val playbackURL = if (
            candidate.headers["resolver"] == "torrent-stream" &&
            (candidate.url.startsWith("http://127.0.0.1:") || candidate.url.startsWith("http://localhost:"))
        ) candidate.url else DesktopMediaProxy.playbackURL(candidate)
        Platform.runLater {
            runCatching {
                player?.dispose()
                val media = Media(playbackURL).apply {
                    setOnError {
                        playbackError = error?.message ?: "JavaFX 內建播放器無法讀取播放源"
                        playbackIsPlaying = false
                    }
                }
                player = MediaPlayer(media).also { next ->
                    mediaView?.mediaPlayer = next
                    next.currentTimeProperty().addListener { _, _, value ->
                        playbackPositionSeconds = value?.toSeconds()?.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0
                    }
                    next.totalDurationProperty().addListener { _, _, value ->
                        playbackDurationSeconds = value?.toSeconds()?.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0
                    }
                    next.statusProperty().addListener { _, _, value ->
                        playbackIsPlaying = value == MediaPlayer.Status.PLAYING
                    }
                    next.setOnError {
                        playbackError = next.error?.message ?: "JavaFX 內建播放器錯誤"
                        playbackIsPlaying = false
                    }
                    next.play()
                }
            }.onFailure { throwable ->
                playbackError = "內建播放器無法建立播放來源：${throwable.message ?: throwable::class.simpleName}"
                playbackIsPlaying = false
            }
        }
    }

    fun dispatch(command: WebRuntimeCommand) {
        libVLC?.let {
            it.dispatch(command)
            return
        }
        Platform.runLater {
            val current = player ?: return@runLater
            when (command) {
                WebRuntimeCommand.PlayPause, WebRuntimeCommand.Select -> {
                    if (current.status == MediaPlayer.Status.PLAYING) current.pause() else current.play()
                }
                WebRuntimeCommand.Rewind -> current.seek(current.currentTime.subtract(Duration.seconds(10.0)).coerceAtLeast(Duration.ZERO))
                WebRuntimeCommand.FastForward -> current.seek(current.currentTime.add(Duration.seconds(10.0)).coerceAtMost(current.totalDuration))
                WebRuntimeCommand.VolumeUp -> current.volume = (current.volume + .1).coerceAtMost(1.0)
                WebRuntimeCommand.VolumeDown -> current.volume = (current.volume - .1).coerceAtLeast(0.0)
                WebRuntimeCommand.Mute -> current.isMute = !current.isMute
                else -> Unit
            }
        }
    }

    fun dispose() {
        DesktopAnimePlaybackRegistry.unregister(registryOwner)
        libVLC?.let {
            it.close()
            return
        }
        Platform.runLater {
            player?.stop()
            player?.dispose()
            player = null
        }
    }

    private fun snapshot(): AnimePlaybackSnapshot = libVLC?.snapshot() ?: AnimePlaybackSnapshot(
        positionSeconds = playbackPositionSeconds,
        durationSeconds = playbackDurationSeconds,
        isPlaying = playbackIsPlaying,
        error = playbackError,
    )
}

private fun Duration.coerceAtLeast(minimum: Duration): Duration = if (lessThan(minimum)) minimum else this
private fun Duration.coerceAtMost(maximum: Duration): Duration = when {
    maximum.isUnknown || maximum.isIndefinite -> this
    greaterThan(maximum) -> maximum
    else -> this
}
