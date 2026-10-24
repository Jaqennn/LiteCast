/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AvTransportTest {

    private class FakeHost : AvTransport.Host {
        val calls = mutableListOf<String>()
        override fun dlnaLoad(uri: String, title: String, mime: String, autoPlay: Boolean) { calls += "load:$uri:$autoPlay:$mime:$title" }
        override fun dlnaSetPlaying(playing: Boolean) { calls += "playing:$playing" }
        override fun dlnaStop() { calls += "stop" }
        override fun dlnaSeek(positionMs: Long) { calls += "seek:$positionMs" }
        override fun dlnaSetSpeed(speed: Float) { calls += "speed:$speed" }
    }

    private class FakeScheduler : AvTransport.Scheduler {
        val tasks = mutableListOf<Runnable>()
        override fun schedule(delayMs: Long, task: Runnable) { tasks += task }
        override fun cancel(task: Runnable) { tasks.remove(task) }
        fun runAll() { val copy = tasks.toList(); tasks.clear(); copy.forEach { it.run() } }
    }

    private lateinit var host: FakeHost
    private lateinit var scheduler: FakeScheduler
    private var now = 1_000L
    private var changes = 0
    private lateinit var av: AvTransport

    private val url = "http://cdn.example.com/live/index.m3u8?token=abc"
    private val meta = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/"><item><dc:title>1818黄金眼</dc:title><res protocolInfo="http-get:*:video/mp4:*">x</res></item></DIDL-Lite>"""

    @Before
    fun setUp() {
        host = FakeHost()
        scheduler = FakeScheduler()
        changes = 0
        av = AvTransport(host, scheduler, { now }, {}, { changes++ })
    }

    private fun setUri(uri: String = url, metadata: String = meta) =
        av.action("SetAVTransportURI", mapOf("InstanceID" to "0", "CurrentURI" to uri, "CurrentURIMetaData" to metadata))

    private fun state() = av.action("GetTransportInfo", mapOf("InstanceID" to "0"))["CurrentTransportState"]

    private fun ready(playing: Boolean, position: Long = 0, duration: Long = 600_000, live: Boolean = false) =
        av.onSnapshot(AvTransport.Snapshot(positionMs = position, durationMs = duration, playWhenReady = playing, ready = true, live = live, seekable = !live))

    @Test
    fun startsWithNoMedia() {
        assertEquals("NO_MEDIA_PRESENT", state())
    }

    @Test
    fun setUriPreloadsPausedThenPlayResumesWithoutReloading() {
        setUri()
        assertEquals(listOf("load:$url:false:${MediaProbe.HLS}:1818黄金眼"), host.calls)
        assertEquals("STOPPED", state())
        av.action("Play", mapOf("InstanceID" to "0", "Speed" to "1"))
        assertEquals("playing:true", host.calls.last())
        assertEquals(1, host.calls.count { it.startsWith("load:") })
        assertEquals("TRANSITIONING", state())
        ready(playing = true)
        assertEquals("PLAYING", state())
        assertTrue(scheduler.tasks.isEmpty())
    }

    @Test
    fun autoPlaysWhenSenderNeverSendsPlay() {
        setUri()
        assertEquals(1, scheduler.tasks.size)
        scheduler.runAll()
        assertEquals("playing:true", host.calls.last())
    }

    @Test
    fun sameUriWhilePlayingDoesNotReload() {
        setUri()
        av.action("Play", emptyMap())
        ready(playing = true)
        setUri()
        assertEquals(1, host.calls.count { it.startsWith("load:") })
        assertEquals("PLAYING", state())
    }

    @Test
    fun newUriReplacesCurrentOne() {
        setUri()
        av.action("Play", emptyMap())
        setUri("http://cdn.example.com/other.mp4", "")
        assertEquals("load:http://cdn.example.com/other.mp4:false::", host.calls.last())
    }

    @Test
    fun ignoresOneSpuriousSeekToZeroRightAfterStart() {
        setUri()
        av.action("Play", emptyMap())
        av.action("Seek", mapOf("Unit" to "REL_TIME", "Target" to "00:00:00"))
        assertTrue(host.calls.none { it.startsWith("seek:") })
        av.action("Seek", mapOf("Unit" to "REL_TIME", "Target" to "00:00:00"))
        assertEquals("seek:0", host.calls.last())
        av.action("Seek", mapOf("Unit" to "ABS_TIME", "Target" to "0:01:30.5"))
        assertEquals("seek:90500", host.calls.last())
    }

    @Test
    fun seekAfterWindowIsHonoured() {
        setUri()
        av.action("Play", emptyMap())
        now += AvTransport.INITIAL_SEEK_WINDOW_MS + 1
        av.action("Seek", mapOf("Unit" to "REL_TIME", "Target" to "00:00:00"))
        assertEquals("seek:0", host.calls.last())
    }

    @Test
    fun liveWithoutWindowRejectsSeek() {
        setUri()
        av.action("Play", emptyMap())
        now += AvTransport.INITIAL_SEEK_WINDOW_MS + 1
        ready(playing = true, duration = 0, live = true)
        try {
            av.action("Seek", mapOf("Unit" to "REL_TIME", "Target" to "00:10:00"))
            fail("expected fault")
        } catch (e: Dlna.Fault) {
            assertEquals(710, e.code)
        }
        assertEquals("00:00:00", av.action("GetPositionInfo", emptyMap())["TrackDuration"])
    }

    @Test
    fun pauseAndStopFollowUpnpStates() {
        setUri()
        av.action("Play", emptyMap())
        ready(playing = true, position = 5_000)
        av.action("Pause", emptyMap())
        assertEquals("playing:false", host.calls.last())
        ready(playing = false, position = 5_000)
        assertEquals("PAUSED_PLAYBACK", state())
        av.action("Stop", emptyMap())
        assertEquals("stop", host.calls.last())
        assertEquals("STOPPED", state())
        av.action("Play", emptyMap())
        assertEquals("load:$url:true:${MediaProbe.HLS}:1818黄金眼", host.calls.last())
    }

    @Test
    fun positionInfoReportsPlayerPosition() {
        setUri()
        av.action("Play", emptyMap())
        ready(playing = true, position = 3_723_000, duration = 7_200_000)
        val info = av.action("GetPositionInfo", emptyMap())
        assertEquals("01:02:03", info["RelTime"])
        assertEquals("02:00:00", info["TrackDuration"])
        assertEquals(url, info["TrackURI"])
    }

    @Test
    fun errorIsReportedAndSameUriCanBeRetried() {
        setUri()
        av.action("Play", emptyMap())
        av.onError()
        val info = av.action("GetTransportInfo", emptyMap())
        assertEquals("STOPPED", info["CurrentTransportState"])
        assertEquals("ERROR_OCCURRED", info["CurrentTransportStatus"])
        setUri()
        assertEquals(2, host.calls.count { it.startsWith("load:") })
        assertEquals("OK", av.action("GetTransportInfo", emptyMap())["CurrentTransportStatus"])
    }

    @Test
    fun takenOverByAnotherCastReportsStopped() {
        setUri()
        av.action("Play", emptyMap())
        ready(playing = true)
        av.onInactive()
        assertEquals("STOPPED", state())
        assertTrue(changes > 0)
    }

    @Test
    fun rejectsNonHttpUris() {
        for (bad in listOf("file:///sdcard/a.mp4", "rtsp://cam/stream", "http://user:pw@host/a.mp4", "not a uri")) {
            try {
                setUri(bad, "")
                fail("expected fault for $bad")
            } catch (e: Dlna.Fault) {
                assertEquals(714, e.code)
            }
        }
    }

    @Test
    fun playWithoutMediaFaults() {
        try {
            av.action("Play", emptyMap())
            fail("expected fault")
        } catch (e: Dlna.Fault) {
            assertEquals(701, e.code)
        }
    }

    @Test
    fun emptyUriClearsMedia() {
        setUri()
        av.action("Play", emptyMap())
        setUri("", "")
        assertEquals("stop", host.calls.last())
        assertEquals("NO_MEDIA_PRESENT", state())
    }

    @Test
    fun speedParsing() {
        assertEquals(1f, AvTransport.parseSpeed(null))
        assertEquals(1f, AvTransport.parseSpeed(""))
        assertEquals(1.5f, AvTransport.parseSpeed("3/2"))
        assertEquals(2f, AvTransport.parseSpeed("2"))
        assertEquals(1f, AvTransport.parseSpeed("1/0"))
        assertEquals(1f, AvTransport.parseSpeed("fast"))
        assertEquals("3/2", AvTransport.speedProtocol(1.5f))
    }

    @Test
    fun playWithSpeedAppliesItForVideoOnDemand() {
        setUri()
        av.action("Play", mapOf("Speed" to "2"))
        assertEquals("speed:2.0", host.calls.last())
    }
}
