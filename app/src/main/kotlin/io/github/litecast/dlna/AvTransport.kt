/*
 * Copyright (C) 2026 小狗便利店投屏 contributors
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * AVTransport state machine. The action handling follows puppy-store-cast's ReceiverService
 * (小狗便利店投屏); it is kept free of Android types so it can be unit-tested on the JVM.
 *
 * Sender quirks handled here:
 *  - SetAVTransportURI without a following Play (auto-play after a short grace period)
 *  - the same URI being set again while it is already playing (ignored, no reload)
 *  - a spurious Seek(0) right after playback starts (ignored once, as SonyCast-AndroidTV does)
 */

package io.github.litecast.dlna

import java.net.URI

class AvTransport(
    private val host: Host,
    private val scheduler: Scheduler,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
    private val onChanged: () -> Unit,
) {
    /** Implemented by the service. Calls arrive on worker threads; implementations post to main. */
    interface Host {
        /** Replace whatever is playing with [uri]; start paused unless [autoPlay]. */
        fun dlnaLoad(uri: String, title: String, mime: String, autoPlay: Boolean)
        fun dlnaSetPlaying(playing: Boolean)
        fun dlnaStop()
        fun dlnaSeek(positionMs: Long)
        fun dlnaSetSpeed(speed: Float)
    }

    interface Scheduler {
        fun schedule(delayMs: Long, task: Runnable)
        fun cancel(task: Runnable)
    }

    /** Player state as last reported by the service (updated a few times per second). */
    data class Snapshot(
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val playWhenReady: Boolean = false,
        val ready: Boolean = false,
        val buffering: Boolean = false,
        val live: Boolean = false,
        val seekable: Boolean = false,
        val speed: Float = 1f,
    )

    private val lock = Any()
    var currentUri = ""
        private set
    var metadata = ""
        private set
    var title = ""
        private set
    private var mime = ""
    var active = false          // our URI currently owns the shared player
        private set
    private var desiredPlay = false
    private var everReady = false
    private var transportStatus = "OK"
    private var loadedAt = 0L
    private var initialSeekFiltered = false
    @Volatile private var snapshot = Snapshot()
    private var lastPublishedState = ""
    private val autoPlay = Runnable { onAutoPlay() }

    // ---------------------------------------------------------------- service callbacks

    fun onSnapshot(value: Snapshot) {
        snapshot = value
        synchronized(lock) {
            if (active && value.ready && !everReady) everReady = true
            if (transportState() != lastPublishedState) changed()
        }
    }

    /** Playback finished naturally, was stopped on the TV, or was taken over by another cast. */
    fun onInactive() = synchronized(lock) {
        if (!active) return@synchronized
        active = false
        desiredPlay = false
        scheduler.cancel(autoPlay)
        changed()
    }

    fun onError() = synchronized(lock) {
        active = false
        desiredPlay = false
        transportStatus = "ERROR_OCCURRED"
        scheduler.cancel(autoPlay)
        changed()
    }

    // ---------------------------------------------------------------- SOAP

    fun action(action: String, args: Map<String, String>): Map<String, String> = synchronized(lock) {
        when (action) {
            "SetAVTransportURI" -> setUri(args["CurrentURI"]?.trim().orEmpty(), args["CurrentURIMetaData"].orEmpty())
            "SetNextAVTransportURI" -> Unit // playlists are not supported; accept so senders keep going
            "Play" -> {
                if (currentUri.isEmpty()) throw Dlna.Fault(701, "No media")
                scheduler.cancel(autoPlay)
                desiredPlay = true
                if (!active) beginLoad(autoPlay = true) else host.dlnaSetPlaying(true)
                val speed = parseSpeed(args["Speed"])
                if (speed != 1f && !snapshot.live) host.dlnaSetSpeed(speed)
            }
            "Pause" -> {
                if (currentUri.isEmpty()) throw Dlna.Fault(701, "No media")
                scheduler.cancel(autoPlay)
                desiredPlay = false
                if (active) host.dlnaSetPlaying(false)
            }
            "Stop" -> {
                scheduler.cancel(autoPlay)
                desiredPlay = false
                if (active) {
                    active = false
                    host.dlnaStop()
                }
            }
            "Seek" -> seek(args)
            "Next", "Previous" -> Unit
            "GetTransportInfo" -> return@synchronized Dlna.values(
                "CurrentTransportState", transportState(),
                "CurrentTransportStatus", transportStatus,
                "CurrentSpeed", speedProtocol(snapshot.speed),
            )
            "GetPositionInfo" -> {
                val s = snapshot
                val position = if (active) Dlna.time(s.positionMs) else "00:00:00"
                return@synchronized Dlna.values(
                    "Track", if (currentUri.isEmpty()) "0" else "1",
                    "TrackDuration", duration(),
                    "TrackMetaData", metadata,
                    "TrackURI", currentUri,
                    "RelTime", position,
                    "AbsTime", position,
                    "RelCount", "2147483647",
                    "AbsCount", "2147483647",
                )
            }
            "GetMediaInfo" -> return@synchronized Dlna.values(
                "NrTracks", if (currentUri.isEmpty()) "0" else "1",
                "MediaDuration", duration(),
                "CurrentURI", currentUri,
                "CurrentURIMetaData", metadata,
                "NextURI", "", "NextURIMetaData", "",
                "PlayMedium", "NETWORK", "RecordMedium", "NOT_IMPLEMENTED", "WriteStatus", "NOT_IMPLEMENTED",
            )
            "GetDeviceCapabilities" -> return@synchronized Dlna.values("PlayMedia", "NETWORK", "RecMedia", "NOT_IMPLEMENTED", "RecQualityModes", "NOT_IMPLEMENTED")
            "GetTransportSettings" -> return@synchronized Dlna.values("PlayMode", "NORMAL", "RecQualityMode", "NOT_IMPLEMENTED")
            "GetCurrentTransportActions" -> return@synchronized Dlna.values("Actions", transportActions())
            else -> throw Dlna.Fault(401, "Invalid Action")
        }
        changed()
        emptyMap()
    }

    fun eventValues(): Map<String, String> = synchronized(lock) {
        Dlna.values(
            "TransportState", transportState(),
            "TransportStatus", transportStatus,
            "TransportPlaySpeed", speedProtocol(snapshot.speed),
            "AVTransportURI", currentUri,
            "AVTransportURIMetaData", metadata,
            "CurrentTrackURI", currentUri,
            "CurrentTrackMetaData", metadata,
            "NumberOfTracks", if (currentUri.isEmpty()) "0" else "1",
            "CurrentTrack", if (currentUri.isEmpty()) "0" else "1",
            "CurrentTrackDuration", duration(),
            "CurrentMediaDuration", duration(),
            "CurrentTransportActions", transportActions(),
        )
    }

    fun transportStateForTest(): String = synchronized(lock) { transportState() }

    // ---------------------------------------------------------------- internals (lock held)

    private fun setUri(uri: String, meta: String) {
        if (uri.isEmpty()) {
            scheduler.cancel(autoPlay)
            if (active) host.dlnaStop()
            active = false
            desiredPlay = false
            currentUri = ""; metadata = ""; title = ""; mime = ""
            return
        }
        val parsed = runCatching { URI(uri) }.getOrNull()
        if (parsed == null || parsed.scheme?.lowercase() !in setOf("http", "https") || parsed.host.isNullOrEmpty() || parsed.userInfo != null) {
            throw Dlna.Fault(714, "Only HTTP(S) media supported")
        }
        val newTitle = Xml.first(meta, "title").trim()
        if (uri == currentUri && active && transportStatus == "OK") {
            // Some senders re-send the URI they are already playing; reloading would restart it.
            metadata = meta
            if (newTitle.isNotEmpty()) title = newTitle
            log("DLNA: same URI re-sent, kept playing")
            return
        }
        currentUri = uri
        metadata = meta
        title = newTitle
        val declared = MediaProbe.mime(Xml.resourceMime(meta))
        mime = MediaProbe.initialMime(uri, declared)
        desiredPlay = false
        log("DLNA: received media from ${parsed.host}" + if (declared.isEmpty()) "" else " ($declared)")
        // Preload paused so Play starts instantly; senders that never send Play get auto-play.
        beginLoad(autoPlay = false)
        scheduler.cancel(autoPlay)
        scheduler.schedule(AUTO_PLAY_GRACE_MS, autoPlay)
    }

    private fun seek(args: Map<String, String>) {
        val unit = args["Unit"]?.trim()?.uppercase().orEmpty()
        if (unit != "REL_TIME" && unit != "ABS_TIME") throw Dlna.Fault(710, "Seek mode not supported")
        val target = Dlna.millis(args["Target"].orEmpty())
        val sinceLoad = clock() - loadedAt
        if (target < 1000 && !initialSeekFiltered && sinceLoad < INITIAL_SEEK_WINDOW_MS && snapshot.positionMs < 5000) {
            initialSeekFiltered = true
            log("DLNA: ignored initial Seek(0)")
            return
        }
        if (!active) return
        if (snapshot.live && !snapshot.seekable) throw Dlna.Fault(710, "Source does not support seeking")
        host.dlnaSeek(target)
    }

    /** Marks our URI as owning the player before the (asynchronous) load is posted. */
    private fun beginLoad(autoPlay: Boolean) {
        active = true
        everReady = false
        snapshot = Snapshot()
        loadedAt = clock()
        initialSeekFiltered = false
        transportStatus = "OK"
        host.dlnaLoad(currentUri, title, mime, autoPlay)
    }

    private fun onAutoPlay() = synchronized(lock) {
        if (currentUri.isEmpty() || desiredPlay) return@synchronized
        desiredPlay = true
        if (active) host.dlnaSetPlaying(true) else beginLoad(autoPlay = true)
        log("DLNA: no Play received, starting playback")
        changed()
    }

    private fun changed() {
        lastPublishedState = transportState()
        onChanged()
    }

    private fun transportState(): String {
        val s = snapshot
        return when {
            currentUri.isEmpty() -> "NO_MEDIA_PRESENT"
            !active -> "STOPPED"
            !desiredPlay && !s.playWhenReady -> if (everReady) "PAUSED_PLAYBACK" else "STOPPED"
            !everReady -> "TRANSITIONING"
            s.playWhenReady -> "PLAYING"
            else -> "PAUSED_PLAYBACK"
        }
    }

    private fun transportActions(): String {
        if (currentUri.isEmpty()) return ""
        val s = snapshot
        return if (active && (s.seekable || !s.live)) "Play,Stop,Pause,Seek" else "Play,Stop,Pause"
    }

    private fun duration(): String {
        val s = snapshot
        return if (!active || s.live || s.durationMs <= 0) "00:00:00" else Dlna.time(s.durationMs)
    }

    companion object {
        const val AUTO_PLAY_GRACE_MS = 1500L
        const val INITIAL_SEEK_WINDOW_MS = 6000L

        fun parseSpeed(value: String?): Float {
            if (value.isNullOrBlank()) return 1f
            return runCatching {
                val parts = value.trim().split("/")
                val speed = if (parts.size == 2) parts[0].toFloat() / parts[1].toFloat() else parts[0].toFloat()
                if (speed.isFinite() && speed in 0.25f..4f) speed else 1f
            }.getOrDefault(1f)
        }

        fun speedProtocol(speed: Float): String = when (speed) {
            0.5f -> "1/2"; 0.75f -> "3/4"; 1.25f -> "5/4"; 1.5f -> "3/2"; 2f -> "2"; else -> "1"
        }
    }
}
