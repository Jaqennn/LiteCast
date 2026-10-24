/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.dlna

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Talks to the real HTTP/SOAP/GENA server over sockets the way phone apps do. */
class DlnaHttpIntegrationTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val calls = CopyOnWriteArrayList<String>()
    private lateinit var server: HttpServer
    private lateinit var av: AvTransport

    @Before
    fun setUp() {
        val host = object : AvTransport.Host {
            override fun dlnaLoad(uri: String, title: String, mime: String, autoPlay: Boolean) { calls += "load:$title:$autoPlay" }
            override fun dlnaSetPlaying(playing: Boolean) { calls += "playing:$playing" }
            override fun dlnaStop() { calls += "stop" }
            override fun dlnaSeek(positionMs: Long) { calls += "seek:$positionMs" }
            override fun dlnaSetSpeed(speed: Float) { calls += "speed:$speed" }
        }
        val scheduler = object : AvTransport.Scheduler {
            override fun schedule(delayMs: Long, task: Runnable) {}
            override fun cancel(task: Runnable) {}
        }
        var serverRef: HttpServer? = null
        av = AvTransport(host, scheduler, { 0L }, {}, { serverRef?.publish() })
        val controller = object : Dlna.Controller {
            override fun action(service: String, action: String, args: Map<String, String>): Map<String, String> =
                if (service == Dlna.AV) av.action(action, args) else Dlna.values("CurrentVolume", "30")
            override fun event(service: String): Map<String, String> =
                if (service == Dlna.AV) av.eventValues() else Dlna.values("Volume", "30")
        }
        server = HttpServer(loopback, 0, "test-uuid", { "客厅电视 · 轻投" }, "0.1.0", controller) {}
        serverRef = server
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun port() = server.base().substringAfterLast(':').toInt()

    private fun send(raw: ByteArray): Pair<Int, String> {
        Socket(loopback, port()).use { s ->
            s.soTimeout = 5000
            s.getOutputStream().write(raw)
            s.getOutputStream().flush()
            val text = s.getInputStream().readBytes().toString(Charsets.UTF_8)
            val status = text.substringAfter(' ').substringBefore(' ').toInt()
            return status to text.substringAfter("\r\n\r\n")
        }
    }

    private fun soap(action: String, args: String, soapAction: String?, service: String = Dlna.AV): Pair<Int, String> {
        val ns = Dlna.type(service)
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:$action xmlns:u=\"$ns\">$args</u:$action></s:Body></s:Envelope>"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("POST /control/$service HTTP/1.1\r\nHOST: 127.0.0.1\r\nCONTENT-TYPE: text/xml; charset=\"utf-8\"\r\n")
            if (soapAction != null) append("SOAPACTION: $soapAction\r\n")
            append("CONTENT-LENGTH: ${bytes.size}\r\nConnection: close\r\n\r\n")
        }
        return send(header.toByteArray(Charsets.US_ASCII) + bytes)
    }

    @Test
    fun descriptionAndScpdAreServed() {
        val (status, body) = send("GET /description.xml HTTP/1.1\r\nHost: tv\r\n\r\n".toByteArray())
        assertEquals(200, status)
        assertTrue(body.contains("<friendlyName>客厅电视 · 轻投</friendlyName>"))
        val (scpdStatus, scpd) = send("GET /service/AVTransport.xml HTTP/1.1\r\n\r\n".toByteArray())
        assertEquals(200, scpdStatus)
        assertTrue(scpd.contains("SetAVTransportURI"))
    }

    @Test
    fun typicalAppSessionWorksWithSloppyRequests() {
        val meta = "&lt;DIDL-Lite xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot; xmlns:dc=&quot;http://purl.org/dc/elements/1.1/&quot;&gt;" +
            "&lt;item&gt;&lt;dc:title&gt;浙江卫视直播&lt;/dc:title&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;"
        val (set, _) = soap(
            "SetAVTransportURI",
            "<InstanceID>0</InstanceID><CurrentURI>http://cdn.example.com/live.m3u8?t=1&amp;s=2</CurrentURI><CurrentURIMetaData>$meta</CurrentURIMetaData>",
            "\"${Dlna.type(Dlna.AV)}#SetAVTransportURI\"",
        )
        assertEquals(200, set)
        assertEquals("load:浙江卫视直播:false", calls.last())

        // no SOAPACTION header, no InstanceID, no Speed: still accepted
        val (play, playBody) = soap("Play", "", null)
        assertEquals(200, play)
        assertTrue(playBody.contains("PlayResponse"))
        assertEquals("playing:true", calls.last())

        val (_, info) = soap("GetTransportInfo", "<InstanceID>0</InstanceID>", "${Dlna.type(Dlna.AV)}#GetTransportInfo")
        assertTrue(info.contains("<CurrentTransportState>TRANSITIONING</CurrentTransportState>"))

        val (_, media) = soap("GetMediaInfo", "<InstanceID>0</InstanceID>", null)
        assertTrue(media.contains("http://cdn.example.com/live.m3u8?t=1&amp;s=2"))
    }

    @Test
    fun unknownActionsAndConflictsAreUpnpFaults() {
        val (status, body) = soap("Explode", "<InstanceID>0</InstanceID>", null)
        assertEquals(500, status)
        assertTrue(body.contains("<errorCode>401</errorCode>"))
        val (conflict, conflictBody) = soap("Play", "", "\"${Dlna.type(Dlna.AV)}#Stop\"")
        assertEquals(500, conflict)
        assertTrue(conflictBody.contains("<errorCode>401</errorCode>"))
        val (missing, missingBody) = soap("SetAVTransportURI", "<InstanceID>0</InstanceID>", null)
        assertEquals(500, missing)
        assertTrue(missingBody.contains("<errorCode>402</errorCode>"))
    }

    @Test
    fun subscribersReceiveLastChangeEvents() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(2)
        ServerSocket(0, 8, loopback).use { listener ->
            val thread = Thread {
                repeat(2) {
                    runCatching {
                        listener.accept().use { c ->
                            c.soTimeout = 3000
                            val reader = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
                            val head = StringBuilder()
                            var length = 0
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                head.append(line).append('\n')
                                if (line.lowercase().startsWith("content-length:")) length = line.substringAfter(':').trim().toInt()
                            }
                            val body = CharArray(length)
                            var read = 0
                            while (read < length) {
                                val n = reader.read(body, read, length - read)
                                if (n < 0) break
                                read += n
                            }
                            received += head.toString() + String(body, 0, read)
                            c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        }
                    }
                    latch.countDown()
                }
            }.apply { isDaemon = true; start() }

            val subscribe = "SUBSCRIBE /event/AVTransport HTTP/1.1\r\nHOST: 127.0.0.1\r\nCALLBACK: <http://127.0.0.1:${listener.localPort}/notify>\r\n" +
                "NT: upnp:event\r\nTIMEOUT: Second-300\r\n\r\n"
            val (status, _) = send(subscribe.toByteArray())
            assertEquals(200, status)
            Thread.sleep(400) // initial event
            soap("SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>http://a.example/v.mp4</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>", null)
            assertTrue("events not delivered", latch.await(5, TimeUnit.SECONDS))
            thread.join(1000)
        }
        assertTrue(received.all { it.startsWith("NOTIFY /notify HTTP/1.1") })
        assertTrue(received.any { it.contains("NO_MEDIA_PRESENT") })
        assertTrue(received.any { it.contains("http://a.example/v.mp4") })
    }

    @Test
    fun callbacksToOtherHostsAreRefused() {
        val subscribe = "SUBSCRIBE /event/AVTransport HTTP/1.1\r\nCALLBACK: <http://10.9.9.9:1234/>\r\nNT: upnp:event\r\n\r\n"
        assertEquals(412, send(subscribe.toByteArray()).first)
    }
}
