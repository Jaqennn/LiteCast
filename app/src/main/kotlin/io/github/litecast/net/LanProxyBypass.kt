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
import java.util.Locale

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
            val host = rawHost.trim().removePrefix("[").removeSuffix("]").trimEnd('.').lowercase(Locale.ROOT)
            if (host.isEmpty()) return false
            if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true
            ipv4(host)?.let { return isLocalIpv4(it) }
            if (':' !in host) return false
            val bytes = ipv6(host.substringBefore('%')) ?: return false
            // :: (unspecified) or ::1 (loopback)
            var allZeroPrefix = true
            for (i in 0 until 15) if (bytes[i] != 0) { allZeroPrefix = false; break }
            if (allZeroPrefix && (bytes[15] == 0 || bytes[15] == 1)) return true
            // ::ffff:0:0/96 (IPv4-mapped IPv6)
            var v4Mapped = true
            for (i in 0 until 10) if (bytes[i] != 0) { v4Mapped = false; break }
            if (v4Mapped && bytes[10] == 0xff && bytes[11] == 0xff) {
                return isLocalIpv4(intArrayOf(bytes[12], bytes[13], bytes[14], bytes[15]))
            }
            val first = (bytes[0] shl 8) or bytes[1]
            return first and 0xffc0 == 0xfe80 || // link-local fe80::/10
                first and 0xfe00 == 0xfc00       // unique local fc00::/7
        }

        private fun isLocalIpv4(a: IntArray): Boolean = a[0] == 127 || a[0] == 10 || a[0] == 0 ||
            (a[0] == 172 && a[1] in 16..31) ||
            (a[0] == 192 && a[1] == 168) ||
            (a[0] == 169 && a[1] == 254) ||
            (a[0] == 100 && a[1] in 64..127) // carrier-grade NAT, used by some mesh / travel routers

        private fun ipv6(raw: String): IntArray? {
            if (raw.isEmpty() || raw.contains(":::")) return null
            val halves = raw.split("::", limit = 3)
            if (halves.size > 2) return null
            val left = if (halves[0].isEmpty()) emptyList() else halves[0].split(':')
            val right = if (halves.size == 1 || halves[1].isEmpty()) emptyList() else halves[1].split(':')
            val out = IntArray(16)
            var pos = 0
            fun writeGroups(groups: List<String>, allowV4Last: Boolean): Int {
                var written = 0
                for ((idx, g) in groups.withIndex()) {
                    if (allowV4Last && idx == groups.lastIndex && '.' in g) {
                        val v4 = ipv4(g) ?: return -1
                        if (pos + 4 > 16) return -1
                        for (b in v4) out[pos++] = b
                        written += 2
                    } else {
                        if (g.isEmpty() || g.length > 4) return -1
                        val v = g.toIntOrNull(16) ?: return -1
                        if (v !in 0..0xffff || pos + 2 > 16) return -1
                        out[pos++] = (v ushr 8) and 0xff
                        out[pos++] = v and 0xff
                        written += 1
                    }
                }
                return written
            }
            val leftWords = writeGroups(left, halves.size == 1)
            if (leftWords < 0) return null
            if (halves.size == 1) return if (leftWords == 8 && pos == 16) out else null
            // Count right words (last group may be embedded IPv4 = 2 words)
            val rightHasV4 = right.isNotEmpty() && '.' in right.last()
            val rightWords = right.size + if (rightHasV4) 1 else 0
            val missing = 8 - leftWords - rightWords
            if (missing < 1) return null
            pos += missing * 2
            val writtenRight = writeGroups(right, true)
            return if (writtenRight == rightWords && pos == 16) out else null
        }

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
