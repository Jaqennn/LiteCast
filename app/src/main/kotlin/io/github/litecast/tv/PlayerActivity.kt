/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.audio.TrackInfo
import io.github.jqssun.airplay.service.AirPlayService
import io.github.jqssun.airplay.service.VideoPlaybackInfo
import io.github.jqssun.airplay.ui.VideoSurfaceView
import kotlinx.coroutines.delay

/**
 * Full-screen player that the service pops up for a real session (mirroring, a pushed video,
 * AirPlay music or a PIN prompt) and that closes itself shortly after the session ends.
 */
class PlayerActivity : ComponentActivity() {
    private val binding = ServiceBinding(this)
    private val pin = mutableStateOf<String?>(null)
    private val pinCallback: (String?) -> Unit = { value -> runOnUiThread { pin.value = value } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        binding.onConnected = { svc -> svc.pinCallback = pinCallback }
        setContent {
            LiteCastTheme {
                PlayerScreen(binding.service.value, pin.value, onFinish = ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.bind()
    }

    override fun onStop() {
        binding.service.value?.let { if (it.pinCallback === pinCallback) it.pinCallback = null }
        binding.unbind()
        super.onStop()
    }
}

private enum class Mode { PIN, VIDEO, MIRROR, MUSIC, PREPARING, IDLE }

@Composable
private fun PlayerScreen(svc: AirPlayService?, pin: String?, onFinish: () -> Unit) {
    val video = svc?.videoPlaybackActive.collectOr(false)
    val mirroring = svc?.mirroringActive.collectOr(false)
    val music = svc?.audioOnly.collectOr(false)
    val connections = svc?.connectionCount.collectOr(0)
    val track = svc?.trackInfo.collectOr(TrackInfo())
    val musicDuration = svc?.durationMs.collectOr(0L)
    // audio-only is also reported for a moment after mirroring stops; only real music gets a card
    val musicPlaying = music && connections > 0 && (track.title.isNotEmpty() || musicDuration > 0)
    val pending by produceState(false, svc) {
        while (true) { value = svc?.videoSessionPending() ?: false; delay(250) }
    }
    val mode = when {
        pin != null -> Mode.PIN
        video -> Mode.VIDEO
        mirroring -> Mode.MIRROR
        musicPlaying -> Mode.MUSIC
        pending -> Mode.PREPARING
        else -> Mode.IDLE
    }

    // Close shortly after the last session ends (grace for the session flags to settle at start).
    val openedAt = remember { SystemClock.elapsedRealtime() }
    LaunchedEffect(mode, svc) {
        if (mode != Mode.IDLE || svc == null) return@LaunchedEffect
        delay(if (SystemClock.elapsedRealtime() - openedAt < 4000) 4000 else 1500)
        onFinish()
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when (mode) {
            Mode.PIN -> PinView(pin.orEmpty())
            Mode.VIDEO -> VideoView(svc!!, onFinish)
            Mode.MIRROR -> MirrorView(svc!!, onFinish)
            Mode.MUSIC -> MusicView(svc!!, onFinish)
            Mode.PREPARING -> Waiting(stringResource(R.string.lc_player_preparing))
            Mode.IDLE -> Unit
        }
    }
}

// ------------------------------------------------------------------------------------ video

@Composable
private fun VideoView(svc: AirPlayService, onFinish: () -> Unit) {
    val aspect = svc.videoPlaybackAspect.collectOr(16f / 9f)
    val info = svc.videoPlaybackInfo.collectOr(VideoPlaybackInfo())
    val title = svc.videoTitle.collectOr("")
    val source = svc.videoSource.collectOr(AirPlayService.VideoSource.AIRPLAY)
    val error = svc.videoError.collectOr(null)
    val errorFatal = svc.videoErrorFatal.collectOr(false)
    val sequence = svc.videoPlaySeq.collectOr(0L)

    var overlayUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var lastSeekAt by remember { mutableLongStateOf(0L) }
    val focus = remember { FocusRequester() }

    fun showOverlay(ms: Long = 4000) { overlayUntil = SystemClock.elapsedRealtime() + ms }
    LaunchedEffect(sequence) { showOverlay(5000); seekTarget = null }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            if (seekTarget != null && now - lastSeekAt > 1200) seekTarget = null
            delay(200)
        }
    }
    val overlayVisible = now < overlayUntil || !info.playing || error != null || seekTarget != null

    BackHandler {
        if (overlayVisible && info.playing && error == null) overlayUntil = 0L
        else { svc.stopVideoPlayback(); onFinish() }
    }

    fun seekBy(delta: Long) {
        val duration = info.durationMs
        val base = seekTarget ?: info.positionMs
        val target = (base + delta).coerceAtLeast(0L).let { if (duration > 0) it.coerceAtMost(duration) else it }
        seekTarget = target
        lastSeekAt = SystemClock.elapsedRealtime()
        svc.seekVideoTo(target)
        showOverlay()
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        val forward = native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || native.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                        val held = native.eventTime - native.downTime
                        val tooSoon = native.repeatCount > 0 && SystemClock.elapsedRealtime() - lastSeekAt < 250
                        if (!info.seekable) showOverlay()
                        else if (!tooSoon) seekBy((if (forward) 1 else -1) * seekStep(held))
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                        if (native.repeatCount == 0) { svc.setVideoPlaying(!info.playing); showOverlay() }
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { svc.setVideoPlaying(true); showOverlay(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { svc.setVideoPlaying(false); showOverlay(); true }
                    KeyEvent.KEYCODE_MEDIA_STOP -> { svc.stopVideoPlayback(); onFinish(); true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { showOverlay(); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        VideoSurfaceView(
            onSurfaceAvailable = { svc.setVideoPlaybackSurface(it) },
            onSurfaceDestroyed = { svc.clearVideoPlaybackSurface(it) },
            aspectRatio = aspect,
        )

        if (error != null) {
            ErrorCard(error, fatal = errorFatal)
        } else if (info.buffering) {
            CircularProgressIndicator(color = Tv.Accent, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
        }

        AnimatedVisibility(overlayVisible && error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            VideoOverlay(
                title = title,
                source = if (source == AirPlayService.VideoSource.DLNA) "DLNA" else "AirPlay",
                info = info,
                seekTarget = seekTarget,
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** 10 s per press; holding accelerates to 20/30/40/60 s per step, like a TV remote should. */
private fun seekStep(heldMs: Long): Long = 10_000L * when {
    heldMs < 2000 -> 1
    heldMs < 3000 -> 2
    heldMs < 4000 -> 3
    heldMs < 5000 -> 4
    else -> 6
}

@Composable
internal fun VideoOverlay(title: String, source: String, info: VideoPlaybackInfo, seekTarget: Long?) {
    Box(Modifier.fillMaxSize()) {
        // top: title
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(horizontal = 48.dp, vertical = 32.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceChip(source)
                Spacer(Modifier.width(14.dp))
                Text(title.ifEmpty { " " }, style = Tv.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // centre: paused state
        if (!info.playing && !info.buffering) {
            Box(
                Modifier.align(Alignment.Center).size(96.dp).clip(CircleShape).background(Tv.Scrim),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Tv.TextPrimary, modifier = Modifier.size(56.dp)) }
        }
        // bottom: progress
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 32.dp)
        ) {
            val shown = seekTarget ?: info.positionMs
            // live: a badge instead of a time bar; unknown length (still opening): neither
            Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                if (info.live) {
                    StatusDot(Tv.Error)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.lc_player_live), style = Tv.Label)
                } else if (info.durationMs > 0) {
                    Text(formatTime(shown), style = Tv.Label.copy(color = if (seekTarget != null) Tv.Accent else Tv.TextPrimary))
                    Spacer(Modifier.width(18.dp))
                    ProgressLine(shown.toFloat() / info.durationMs, Modifier.weight(1f))
                    Spacer(Modifier.width(18.dp))
                    Text(formatTime(info.durationMs), style = Tv.Label.copy(color = Tv.TextSecondary))
                }
            }
            Spacer(Modifier.height(14.dp))
            KeyHints(
                listOfNotNull(
                    if (info.seekable) KEY_LEFT_RIGHT to stringResource(R.string.lc_key_seek) else null,
                    stringResource(R.string.lc_key_ok) to stringResource(R.string.lc_key_pause),
                    stringResource(R.string.lc_key_back) to stringResource(R.string.lc_key_exit),
                )
            )
        }
    }
}

@Composable
private fun SourceChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Tv.AccentSoft).padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, style = Tv.Caption.copy(color = Tv.Accent, fontWeight = FontWeight.SemiBold)) }
}

@Composable
internal fun ErrorCard(message: String, fatal: Boolean = false) {
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Tv.Scrim)
            .padding(horizontal = 32.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (fatal) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Tv.Warning, modifier = Modifier.size(36.dp))
        } else {
            CircularProgressIndicator(color = Tv.Warning, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }
        Text(message, style = Tv.Body.copy(color = Tv.TextPrimary))
    }
}

// ------------------------------------------------------------------------------------ mirroring

@Composable
private fun MirrorView(svc: AirPlayService, onFinish: () -> Unit) {
    val aspect = svc.videoAspect.collectOr(16f / 9f)
    var hintUntil by remember { mutableLongStateOf(SystemClock.elapsedRealtime() + 3000) }
    var backArmedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(250) } }

    BackHandler {
        val t = SystemClock.elapsedRealtime()
        if (t < backArmedUntil) {
            svc.disconnectClients()
            onFinish()
        } else {
            backArmedUntil = t + 2500
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        VideoSurfaceView(
            onSurfaceAvailable = { svc.setVideoSurface(it) },
            onSurfaceDestroyed = { svc.clearVideoSurface(it) },
            aspectRatio = aspect,
        )
        AnimatedVisibility(now < hintUntil, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart)) {
            MirrorBadge()
        }
        BackHint(visible = now < backArmedUntil)
    }
}

@Composable
internal fun MirrorBadge() {
    Row(
        Modifier.padding(32.dp).clip(RoundedCornerShape(12.dp)).background(Tv.Scrim).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Cast, contentDescription = null, tint = Tv.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.lc_player_mirroring), style = Tv.Label)
    }
}

@Composable
internal fun BoxScope.BackHint(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
        Box(
            Modifier.padding(bottom = 40.dp).clip(RoundedCornerShape(50)).background(Tv.Scrim).padding(horizontal = 22.dp, vertical = 12.dp)
        ) { Text(stringResource(R.string.lc_player_back_again), style = Tv.Label) }
    }
}

// ------------------------------------------------------------------------------------ music

@Composable
private fun MusicView(svc: AirPlayService, onFinish: () -> Unit) {
    val track = svc.trackInfo.collectOr(TrackInfo())
    val duration = svc.durationMs.collectOr(0L)
    val playing = svc.playing.collectOr(true)
    val position by produceState(0L, svc) { while (true) { value = svc.currentPositionMs(); delay(500) } }
    var backArmedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(250) } }

    BackHandler {
        val t = SystemClock.elapsedRealtime()
        if (t < backArmedUntil) { svc.disconnectClients(); onFinish() } else backArmedUntil = t + 2500
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { svc.togglePlayPause(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> { svc.dacpController?.nextItem(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { svc.dacpController?.prevItem(); true }
                    else -> false
                }
            },
    ) {
        MusicContent(track, position, duration, playing, backHint = now < backArmedUntil)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
internal fun MusicContent(track: TrackInfo, position: Long, duration: Long, playing: Boolean, backHint: Boolean) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Tv.BackgroundTop, Tv.Background))),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 80.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(260.dp).clip(RoundedCornerShape(24.dp)).background(Tv.SurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                val art = track.coverArt
                if (art != null) {
                    Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Tv.TextTertiary, modifier = Modifier.size(96.dp))
                }
            }
            Spacer(Modifier.width(48.dp))
            Column(Modifier.widthIn(max = 560.dp)) {
                Text(stringResource(R.string.lc_player_music), style = Tv.Caption.copy(color = Tv.Accent))
                Spacer(Modifier.height(10.dp))
                Text(track.title.ifEmpty { " " }, style = Tv.Display.copy(fontSize = 36.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(listOf(track.artist, track.album).filter { it.isNotEmpty() }.joinToString(" · "), style = Tv.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(28.dp))
                if (duration > 0) {
                    ProgressLine(position.toFloat() / duration)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Text(formatTime(position), style = Tv.Caption)
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(duration), style = Tv.Caption)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Tv.TextSecondary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    KeyHints(
                        listOf(
                            stringResource(R.string.lc_key_ok) to stringResource(R.string.lc_key_pause),
                            KEY_LEFT_RIGHT to stringResource(R.string.lc_key_tracks),
                            stringResource(R.string.lc_key_back_twice) to stringResource(R.string.lc_key_disconnect),
                        )
                    )
                }
            }
        }
        BackHint(visible = backHint)
    }
}

// ------------------------------------------------------------------------------------ pin / waiting

@Composable
internal fun PinView(pin: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("AirPlay", style = Tv.Caption.copy(color = Tv.Accent))
        Spacer(Modifier.height(18.dp))
        Text(pin.toCharArray().joinToString("  "), style = Tv.Display.copy(fontSize = 96.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp))
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.lc_player_pin), style = Tv.Body)
    }
}

@Composable
internal fun Waiting(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Tv.Accent, strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        Text(text, style = Tv.Body)
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total / 60 % 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
