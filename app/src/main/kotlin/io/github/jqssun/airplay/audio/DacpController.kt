package io.github.jqssun.airplay.audio

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Sends DACP (Digital Audio Control Protocol) commands back to the AirPlay sender.
 * Resolves the sender's control port via mDNS, then sends HTTP GET requests.
 */
class DacpController(ctx: Context) {

    private data class Endpoint(
        val dacpId: String = "",
        val activeRemote: String = "",
        val host: String = "",
        val port: Int = 0,
    )

    private val nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val exec = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()
    private val resolving = AtomicBoolean()
    @Volatile private var endpoint = Endpoint()

    val dacpId: String get() = endpoint.dacpId
    val activeRemote: String get() = endpoint.activeRemote
    val ready: Boolean
        get() = endpoint.let { it.host.isNotEmpty() && it.port > 0 && it.activeRemote.isNotEmpty() }

    fun update(dacpId: String, activeRemote: String) {
        val prev = endpoint
        if (prev.dacpId == dacpId) {
            endpoint = prev.copy(activeRemote = activeRemote)
            if (prev.host.isEmpty() && !resolving.get()) _resolve(dacpId, generation.get())
            return
        }
        val gen = generation.incrementAndGet()
        resolving.set(false)
        endpoint = Endpoint(dacpId = dacpId, activeRemote = activeRemote)
        _resolve(dacpId, gen)
    }

    fun play() = _send("/ctrl-int/1/play")
    fun pause() = _send("/ctrl-int/1/pause")
    fun nextItem() = _send("/ctrl-int/1/nextitem")
    fun prevItem() = _send("/ctrl-int/1/previtem")
    fun volumeUp() = _send("/ctrl-int/1/volumeup")
    fun volumeDown() = _send("/ctrl-int/1/volumedown")
    fun beginFastForward() = _send("/ctrl-int/1/beginff")
    fun beginRewind() = _send("/ctrl-int/1/beginrew")
    fun playResume() = _send("/ctrl-int/1/playresume")

    fun reset() {
        generation.incrementAndGet()
        resolving.set(false)
        endpoint = Endpoint()
    }

    fun release() {
        reset()
        exec.shutdownNow()
    }

    private fun _resolve(targetId: String, gen: Long) {
        if (targetId.isEmpty() || !resolving.compareAndSet(false, true)) return
        val serviceName = "iTunes_Ctrl_$targetId"
        val info = NsdServiceInfo().apply {
            serviceType = "_dacp._tcp"
            this.serviceName = serviceName
        }
        try {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(si: NsdServiceInfo, code: Int) {
                    if (generation.get() == gen) resolving.set(false)
                    Log.w(TAG, "DACP resolve failed: $code")
                }
                override fun onServiceResolved(si: NsdServiceInfo) {
                    if (generation.get() != gen) return
                    resolving.set(false)
                    @Suppress("DEPRECATION")
                    val addr = si.host ?: return
                    val raw = addr.hostAddress ?: return
                    val formatted = if (addr is Inet6Address) "[$raw]" else raw
                    if (si.port <= 0) return
                    val current = endpoint
                    if (generation.get() == gen && current.dacpId == targetId) {
                        endpoint = current.copy(host = formatted, port = si.port)
                        Log.i(TAG, "DACP resolved: $formatted:${si.port}")
                    }
                }
            })
        } catch (e: Exception) {
            if (generation.get() == gen) resolving.set(false)
            Log.w(TAG, "DACP resolve error", e)
        }
    }

    private fun _send(path: String): ListenableFuture<Unit> {
        val result = SettableFuture.create<Unit>()
        val ep = endpoint
        if (ep.host.isEmpty() || ep.port <= 0 || ep.activeRemote.isEmpty()) {
            result.setException(IOException("dacp endpoint not resolved"))
            return result
        }
        try {
            exec.execute {
                var conn: HttpURLConnection? = null
                try {
                    val url = "http://${ep.host}:${ep.port}$path"
                    conn = URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Active-Remote", ep.activeRemote)
                    conn.setRequestProperty("Host", "${ep.host}:${ep.port}")
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    val code = conn.responseCode
                    val stream = if (code in 200..399) conn.inputStream else conn.errorStream
                    try { stream?.use { it.readBytes() } } catch (_: Exception) {}
                    if (code in 200..299) {
                        result.set(Unit)
                    } else {
                        Log.w(TAG, "DACP $path -> HTTP $code")
                        result.setException(IOException("HTTP $code"))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DACP send failed: $path", e)
                    result.setException(e)
                } finally {
                    conn?.disconnect()
                }
            }
        } catch (e: Exception) {
            result.setException(e)
        }
        return result
    }

    companion object {
        private const val TAG = "DacpController"
    }
}
