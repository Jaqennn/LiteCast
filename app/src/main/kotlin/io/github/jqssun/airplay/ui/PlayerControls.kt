/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

// LiteCast: stand-ins for the media3-ui-compose-material3 controls on the AirPlay music screen.
// That library only exists from Media3 1.9, which needs Android 6.0; these are built on the state
// holders of media3-ui-compose 1.8 so the app still installs on Android 5.x TVs. The names and
// parameters match the library's, so MainScreen only changes its imports.
package io.github.jqssun.airplay.ui

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import io.github.jqssun.airplay.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
internal fun PlayPauseButton(player: Player, modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    val state = rememberPlayPauseButtonState(player)
    IconButton(onClick = state::onClick, enabled = state.isEnabled, modifier = modifier) {
        Icon(
            imageVector = if (state.showPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            contentDescription = stringResource(R.string.cd_play_pause),
            modifier = Modifier.size(iconSize)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun PreviousButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberPreviousButtonState(player)
    IconButton(onClick = state::onClick, enabled = state.isEnabled, modifier = modifier) {
        Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.lc_cd_previous))
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun NextButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberNextButtonState(player)
    IconButton(onClick = state::onClick, enabled = state.isEnabled, modifier = modifier) {
        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.lc_cd_next))
    }
}

private class Progress(val positionMs: Long, val durationMs: Long)

private fun Player.progress() = Progress(
    positionMs = currentPosition.coerceAtLeast(0),
    durationMs = duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
)

/** Polls the player twice a second; the music screen is the only user and is rarely open. */
@Composable
private fun rememberProgress(player: Player): Progress {
    var progress by remember(player) { mutableStateOf(player.progress()) }
    LaunchedEffect(player) {
        while (true) {
            progress = player.progress()
            delay(500)
        }
    }
    return progress
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
internal fun PositionText(player: Player, modifier: Modifier = Modifier) {
    Text(formatTime(rememberProgress(player).positionMs), style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

@Composable
internal fun DurationText(player: Player, modifier: Modifier = Modifier) {
    Text(formatTime(rememberProgress(player).durationMs), style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

@Composable
internal fun ProgressSlider(player: Player, modifier: Modifier = Modifier) {
    val progress = rememberProgress(player)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (progress.durationMs > 0) progress.positionMs.toFloat() / progress.durationMs else 0f
    Slider(
        value = dragging ?: fraction.coerceIn(0f, 1f),
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            val target = dragging
            if (target != null && progress.durationMs > 0) player.seekTo((target * progress.durationMs).toLong())
            dragging = null
        },
        enabled = progress.durationMs > 0 && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
        modifier = modifier,
    )
}

/** Cover, title, artist and play / pause in one row; a click opens the full now-playing tab. */
@OptIn(UnstableApi::class)
@Composable
internal fun MiniController(
    player: Player,
    bitmapLoader: BitmapLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var metadata by remember(player) { mutableStateOf(player.mediaMetadata) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                metadata = mediaMetadata
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val artwork by produceState<Bitmap?>(null, metadata) {
        val future = bitmapLoader.loadBitmapFromMetadata(metadata)
        value = if (future == null) null else withContext(Dispatchers.IO) { runCatching { future.get() }.getOrNull() }
    }
    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                val cover = artwork
                if (cover != null) {
                    Image(cover.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    metadata.title?.toString().orEmpty().ifEmpty { stringResource(R.string.unknown_track) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val artist = metadata.artist?.toString().orEmpty()
                if (artist.isNotEmpty()) {
                    Text(
                        artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            PlayPauseButton(player)
        }
    }
}
