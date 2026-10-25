/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.net

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * A TV whose Wi-Fi has a manual proxy (a common way to let it reach sites the home network
 * can't) sends every HTTP request through that proxy, including the AirPlay receiver's own
 * playlist server on localhost and videos that phones serve on the LAN. The proxy can't reach
 * those, so playback would stall. Loopback, private and link-local hosts always go direct;
 * everything else keeps whatever the system chose.
 *
 * Android swaps the default selector back whenever the network's proxy changes, so callers
 * re-[install] right before they connect (see [LanAwareDataSource]).
 */
class LanProxyBypass private constructor(private val system: ProxySelector?) : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        val host = uri?.host
        if (host != null && isLocalHost(host)) return DIRECT
        return system?.select(uri) ?: DIRECT
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        system?.connectFailed(uri, sa, ioe)
    }

    companion object {
        private val DIRECT = listOf(Proxy.NO_PROXY)

        /** Wraps the current default selector unless it is already wrapped. Cheap; call freely. */
        @JvmStatic
        fun install() {
            synchronized(this) {
                val current = ProxySelector.getDefault()
                if (current !is LanProxyBypass) ProxySelector.setDefault(LanProxyBypass(current))
            }
        }

        /** Literal checks only: never resolves a name (that would leak lookups and block). */
        @JvmStatic
        fun isLocalHost(rawHost: String): Boolean {
            val host = rawHost.trim().removePrefix("[").removeSuffix("]").trimEnd('.').lowercase()
            if (host.isEmpty()) return false
            if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true
            ipv4(host)?.let { return isLocalIpv4(it) }
            if (':' !in host) return false
            val v6 = host.substringBefore('%')
            if (v6 == "::1") return true
            if (v6.startsWith("::ffff:")) ipv4(v6.removePrefix("::ffff:"))?.let { return isLocalIpv4(it) }
            val first = v6.substringBefore(':').toIntOrNull(16) ?: return false
            return first and 0xffc0 == 0xfe80 || // link-local fe80::/10
                first and 0xfe00 == 0xfc00       // unique local fc00::/7
        }

        private fun isLocalIpv4(a: IntArray): Boolean = a[0] == 127 || a[0] == 10 || a[0] == 0 ||
            (a[0] == 172 && a[1] in 16..31) ||
            (a[0] == 192 && a[1] == 168) ||
            (a[0] == 169 && a[1] == 254) ||
            (a[0] == 100 && a[1] in 64..127) // carrier-grade NAT, used by some mesh / travel routers

        private fun ipv4(host: String): IntArray? {
            val parts = host.split('.')
            if (parts.size != 4) return null
            val out = IntArray(4)
            for ((i, p) in parts.withIndex()) {
                if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
                out[i] = p.toInt().takeIf { it <= 255 } ?: return null
            }
            return out
        }
    }
}
