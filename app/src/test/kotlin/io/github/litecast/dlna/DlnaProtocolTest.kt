/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.dlna

import java.io.ByteArrayInputStream
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DlnaProtocolTest {

    private val av = Dlna.type(Dlna.AV)

    @Test
    fun timeParsingAcceptsSloppyForms() {
        assertEquals(62_000L, Dlna.millis("00:01:02"))
        assertEquals(62_500L, Dlna.millis("0:01:02.500"))
        assertEquals(62_000L, Dlna.millis("1:02"))
        assertEquals(62_500L, Dlna.millis("62.5"))
        for (bad in listOf("", "abc", "-1", "1:2:3:4")) {
            try {
                Dlna.millis(bad)
                fail("expected fault for '$bad'")
            } catch (e: Dlna.Fault) {
                assertEquals(711, e.code)
            }
        }
        assertEquals("01:02:03", Dlna.time(3_723_999))
    }

    @Test
    fun soapActionHeaderIsTolerant() {
        HttpServer.checkAction("AVTransport", "Play", av, null)
        HttpServer.checkAction("AVTransport", "Play", av, "\"$av#Play\"")
        HttpServer.checkAction("AVTransport", "Play", av, "$av#Play")
        HttpServer.checkAction("AVTransport", "Play", "urn:schemas-upnp-org:service:AVTransport:2", "urn:schemas-upnp-org:service:AVTransport:2#Play")
        HttpServer.checkAction("AVTransport", "Play", "", "")
        for ((ns, header) in listOf(
            av to "$av#Stop",
            "urn:schemas-upnp-org:service:RenderingControl:1" to null,
        )) {
            try {
                HttpServer.checkAction("AVTransport", "Play", ns, header)
                fail("expected fault for $ns / $header")
            } catch (e: Dlna.Fault) {
                assertEquals(401, e.code)
            }
        }
    }

    @Test
    fun missingOptionalArgumentsHaveDefaults() {
        assertEquals("0", Dlna.defaultArgument("InstanceID"))
        assertEquals("1", Dlna.defaultArgument("Speed"))
        assertEquals("", Dlna.defaultArgument("CurrentURIMetaData"))
        assertNull(Dlna.defaultArgument("CurrentURI"))
        assertNull(Dlna.defaultArgument("Target"))
    }

    @Test
    fun requestParsingHandlesChunkedBodiesAndChineseMetadata() {
        val body = "<x>投屏测试</x>"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val chunked = "POST /control/AVTransport HTTP/1.1\r\nHost: tv\r\nTransfer-Encoding: chunked\r\n\r\n" +
            Integer.toHexString(bytes.size) + "\r\n"
        val raw = chunked.toByteArray(Charsets.US_ASCII) + bytes + "\r\n0\r\n\r\n".toByteArray(Charsets.US_ASCII)
        val request = HttpServer.read(ByteArrayInputStream(raw), InetAddress.getLoopbackAddress())
        assertEquals("POST", request.method())
        assertEquals("/control/AVTransport", request.path())
        assertEquals(body, request.body())

        // Content-Length counts bytes, not characters (the classic Chinese-title timeout bug)
        val fixed = ("POST /control/AVTransport?x=1 HTTP/1.1\r\nSOAPACTION: a\r\nsoapaction: b\r\nContent-Length: ${bytes.size}\r\n\r\n")
            .toByteArray(Charsets.US_ASCII) + bytes
        val second = HttpServer.read(ByteArrayInputStream(fixed), InetAddress.getLoopbackAddress())
        assertEquals("/control/AVTransport", second.path())
        assertEquals("b", second.headers()["soapaction"])
        assertEquals(body, second.body())
    }

    @Test
    fun absoluteFormTargetsAreNormalised() {
        val raw = "GET http://192.168.1.20:49595/description.xml HTTP/1.1\r\n\r\n".toByteArray()
        assertEquals("/description.xml", HttpServer.read(ByteArrayInputStream(raw), InetAddress.getLoopbackAddress()).path())
    }

    @Test
    fun ssdpSearchParsing() {
        val quoted = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\nST: ${Dlna.DEVICE}\r\n\r\n"
        val search = SsdpServer.parseSearch(quoted)!!
        assertEquals(Dlna.DEVICE, search.st())
        assertEquals(3, search.mx())
        val unquoted = "M-SEARCH * HTTP/1.1\nMAN: ssdp:discover\nST: ssdp:all\n"
        assertEquals("ssdp:all", SsdpServer.parseSearch(unquoted)!!.st())
        assertNull(SsdpServer.parseSearch("NOTIFY * HTTP/1.1\r\nNT: upnp:rootdevice\r\n\r\n"))
        assertNull(SsdpServer.parseSearch("M-SEARCH * HTTP/1.1\r\nMAN: \"ssdp:discover\"\r\n\r\n"))
        assertEquals(1, SsdpServer.parseSearch("M-SEARCH * HTTP/1.1\r\nMAN: ssdp:discover\r\nMX: x\r\nST: a\r\n")!!.mx())
    }

    @Test
    fun formatSniffing() {
        assertEquals("HLS", MediaProbe.inspect("#EXTM3U\n#EXT-X-VERSION:3".toByteArray(), "", "").kind())
        assertEquals("HLS", MediaProbe.inspect("\uFEFF  #EXTM3U".toByteArray(), "video/mp4", "").kind())
        val mp4 = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray()
        assertEquals("video/mp4", MediaProbe.inspect(mp4, "", "").mime())
        assertEquals("video/x-flv", MediaProbe.inspect("FLV\u0001".toByteArray(), "", "").mime())
        val ts = ByteArray(400).also { it[0] = 0x47; it[188] = 0x47 }
        assertEquals("video/mp2t", MediaProbe.inspect(ts, "", "").mime())
        assertTrue(MediaProbe.inspect("<!DOCTYPE html><html>".toByteArray(), "", "").nonMedia())
        assertTrue(MediaProbe.inspect("{\"code\":403}".toByteArray(), "", "").nonMedia())
        assertEquals(MediaProbe.HLS, MediaProbe.mime("application/vnd.apple.mpegurl; charset=utf-8"))
        assertEquals(MediaProbe.HLS, MediaProbe.initialMime("http://a/b/index.m3u8?t=1", "video/mp4"))
        assertEquals("video/mp4", MediaProbe.initialMime("http://a/b/play", "video/mp4"))
    }

    @Test
    fun xmlHelpersTolerateBomAndNamespaces() {
        val envelope = "\uFEFF  <?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>" +
            "<u:SetAVTransportURI xmlns:u=\"$av\"><InstanceID>0</InstanceID><CurrentURI>http://a/b.m3u8</CurrentURI></u:SetAVTransportURI></s:Body></s:Envelope>"
        val action = Xml.soapAction(envelope)
        assertEquals("SetAVTransportURI", Xml.local(action))
        assertEquals(av, action.namespaceURI)
        assertEquals("http://a/b.m3u8", Xml.arguments(action)["CurrentURI"])
        val didl = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><item><dc:title>浙江新闻</dc:title><res protocolInfo=\"http-get:*:application/vnd.apple.mpegurl:*\">u</res></item></DIDL-Lite>"
        assertEquals("浙江新闻", Xml.first(didl, "title"))
        assertEquals("application/vnd.apple.mpegurl", Xml.resourceMime(didl))
        try {
            Xml.parse("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>")
            fail("DTDs must be rejected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun descriptionCarriesNameAndServices() {
        val xml = Dlna.description("uuid-1", "http://10.0.0.2:49595", "客厅电视 · 轻投", "0.1.0")
        assertTrue(xml.contains("<friendlyName>客厅电视 · 轻投</friendlyName>"))
        assertTrue(xml.contains("<UDN>uuid:uuid-1</UDN>"))
        for (s in Dlna.SERVICES) assertTrue(xml.contains("<controlURL>/control/$s</controlURL>"))
        assertTrue(Dlna.scpd(Dlna.AV).contains("<name>SetAVTransportURI</name>"))
    }
}
