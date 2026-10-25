/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.net

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LanProxyBypassTest {
    private val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("192.168.1.9", 7890))
    private val system = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = listOf(proxy)
        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }
    private var original: ProxySelector? = null

    @Before
    fun setUp() {
        original = ProxySelector.getDefault()
        ProxySelector.setDefault(system)
    }

    @After
    fun tearDown() = ProxySelector.setDefault(original)

    @Test
    fun lanAndLoopbackHostsAreLocal() {
        for (host in listOf(
            "localhost", "LOCALHOST.", "127.0.0.1", "127.8.9.10", "10.0.0.5", "172.16.0.1", "172.31.255.255",
            "192.168.1.20", "169.254.3.4", "100.64.0.1", "[::1]", "::1", "fe80::1%wlan0", "[fd12:3456::1]",
            "::ffff:192.168.1.2", "iPhone.local", "tv.localhost",
        )) assertTrue(host, LanProxyBypass.isLocalHost(host))
    }

    @Test
    fun internetHostsAreNotLocal() {
        for (host in listOf(
            "rr3---sn-a5mekn7s.googlevideo.com", "www.youtube.com", "8.8.8.8", "172.32.0.1", "172.15.0.1",
            "192.169.0.1", "100.128.0.1", "2001:4860:4860::8888", "[2408:8000::1]", "localhost.example.com",
            "evil.local.example", "1.2.3", "300.1.1.1", "01234.1.1.1", "",
        )) assertFalse(host, LanProxyBypass.isLocalHost(host))
    }

    @Test
    fun localhostGoesDirectEverythingElseUsesTheSystemProxy() {
        LanProxyBypass.install()
        val selector = ProxySelector.getDefault()
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI("http://localhost:7000/master.m3u8")))
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI("http://192.168.1.23:49152/video.mp4")))
        assertEquals(listOf(proxy), selector.select(URI("https://rr3---sn-a5mekn7s.googlevideo.com/videoplayback?id=1")))
    }

    @Test
    fun installIsIdempotentAndSurvivesTheSystemSwappingSelectors() {
        LanProxyBypass.install()
        val first = ProxySelector.getDefault()
        LanProxyBypass.install()
        assertSame(first, ProxySelector.getDefault())
        // Android puts its own selector back when the network's proxy changes
        ProxySelector.setDefault(system)
        LanProxyBypass.install()
        assertTrue(ProxySelector.getDefault() is LanProxyBypass)
        assertEquals(listOf(Proxy.NO_PROXY), ProxySelector.getDefault().select(URI("http://127.0.0.1:7000/a.m3u8")))
    }
}
