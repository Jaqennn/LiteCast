/*
 * Copyright (C) 2026 小狗便利店投屏 contributors
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * DLNA MediaRenderer: binds the UPnP HTTP/SSDP endpoints to the current Wi-Fi/Ethernet address
 * (re-binding when the network changes, as puppy-store-cast's ReceiverService does) and answers
 * RenderingControl / ConnectionManager. AVTransport lives in [AvTransport]; playback is delegated
 * to the shared URL player owned by the service.
 */

package io.github.litecast.dlna

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DlnaRenderer(
    private val context: Context,
    private val uuid: String,
    private val friendlyName: () -> String,
    private val version: String,
    host: AvTransport.Host,
    private val log: (String) -> Unit,
) : Dlna.Controller {

    enum class Status { STOPPED, STARTING, READY, ERROR }

    private val main = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var http: HttpServer? = null
    @Volatile private var ssdp: SsdpServer? = null
    @Volatile private var started = false

    @Volatile var address: String = ""
        private set
    @Volatile var status: Status = Status.STOPPED
        private set
    var onStatusChanged: ((Status) -> Unit)? = null

    val transport = AvTransport(
        host = host,
        scheduler = object : AvTransport.Scheduler {
            override fun schedule(delayMs: Long, task: Runnable) { main.postDelayed(task, delayMs) }
            override fun cancel(task: Runnable) { main.removeCallbacks(task) }
        },
        clock = SystemClock::elapsedRealtime,
        log = log,
        onChanged = { http?.publish() },
    )

    // ---------------------------------------------------------------- lifecycle

    fun start() {
        if (started) return
        started = true
        setStatus(Status.STARTING)
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("LiteCast.dlna").apply { setReferenceCounted(false); acquire() }
        }.onFailure { log("DLNA: multicast lock unavailable") }
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.postDelayed(::refreshNetwork, 500) }
            override fun onLost(network: Network) { main.post(::refreshNetwork) }
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) { main.post(::refreshNetwork) }
        }
        callback = cb
        runCatching {
            connectivity.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), cb
            )
        }
        main.post(::refreshNetwork)
    }

    fun stop() {
        if (!started) return
        started = false
        generation.incrementAndGet()
        transport.onInactive()
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
        network.execute { closeServers() }
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
        address = ""
        setStatus(Status.STOPPED)
    }

    fun release() {
        stop()
        network.shutdown()
    }

    private fun setStatus(value: Status) {
        if (status == value) return
        status = value
        main.post { onStatusChanged?.invoke(value) }
    }

    private fun refreshNetwork() {
        if (!started) return
        val candidate = lanAddress()
        val next = candidate?.hostAddress ?: ""
        if (next == address && (next.isEmpty() || http != null)) return
        address = next
        val ticket = generation.incrementAndGet()
        network.execute {
            closeServers()
            if (!started || ticket != generation.get()) return@execute
            if (candidate == null) {
                setStatus(Status.ERROR)
                log("DLNA: no Wi-Fi or Ethernet address")
                return@execute
            }
            var newHttp: HttpServer? = null
            var newSsdp: SsdpServer? = null
            try {
                newHttp = try {
                    HttpServer(candidate, PREFERRED_PORT, uuid, friendlyName, version, this, log)
                } catch (busy: java.io.IOException) {
                    HttpServer(candidate, 0, uuid, friendlyName, version, this, log)
                }
                newSsdp = SsdpServer(candidate, uuid, newHttp.base() + "/description.xml", log)
                if (!started || ticket != generation.get()) {
                    newSsdp.close(); newHttp.close(); return@execute
                }
                http = newHttp
                ssdp = newSsdp
                newHttp.start()
                newSsdp.start()
                setStatus(Status.READY)
                log("DLNA ready on ${newHttp.base()}" + if (newSsdp.isListening) "" else " (announce-only)")
            } catch (e: Exception) {
                newSsdp?.close()
                newHttp?.close()
                setStatus(Status.ERROR)
                log("DLNA start failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun closeServers() {
        ssdp?.close(); ssdp = null
        http?.close(); http = null
    }

    private fun lanAddress(): InetAddress? {
        val networks = connectivity.allNetworks.toMutableList()
        connectivity.activeNetwork?.let { active -> networks.remove(active); networks.add(0, active) }
        for (n in networks) {
            val caps = connectivity.getNetworkCapabilities(n) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) continue
            val links = connectivity.getLinkProperties(n) ?: continue
            for (link in links.linkAddresses) {
                val a = link.address
                if (a is Inet4Address && !a.isLoopbackAddress && !a.isLinkLocalAddress) return a
            }
        }
        return null
    }

    // ---------------------------------------------------------------- SOAP

    override fun action(service: String, action: String, args: Map<String, String>): Map<String, String> = when (service) {
        Dlna.CM -> connectionManager(action, args)
        Dlna.RC -> renderingControl(action, args)
        else -> transport.action(action, args)
    }

    override fun event(service: String): Map<String, String> = when (service) {
        Dlna.AV -> transport.eventValues()
        Dlna.RC -> Dlna.values("Volume", volume().toString(), "Mute", if (isMuted()) "1" else "0")
        else -> Dlna.values("SourceProtocolInfo", "", "SinkProtocolInfo", Dlna.PROTOCOLS, "CurrentConnectionIDs", "0")
    }

    private fun connectionManager(action: String, args: Map<String, String>): Map<String, String> = when (action) {
        "GetProtocolInfo" -> Dlna.values("Source", "", "Sink", Dlna.PROTOCOLS)
        "GetCurrentConnectionIDs" -> Dlna.values("ConnectionIDs", "0")
        "GetCurrentConnectionInfo" -> {
            if (args["ConnectionID"]?.trim() != "0") throw Dlna.Fault(706, "No such connection")
            Dlna.values("RcsID", "0", "AVTransportID", "0", "ProtocolInfo", "http-get:*:*:*", "PeerConnectionManager", "",
                "PeerConnectionID", "-1", "Direction", "Input", "Status", "OK")
        }
        else -> throw Dlna.Fault(401, "Invalid Action")
    }

    private fun renderingControl(action: String, args: Map<String, String>): Map<String, String> {
        when (action) {
            "GetVolume" -> return Dlna.values("CurrentVolume", volume().toString())
            "SetVolume" -> {
                val v = args["DesiredVolume"]?.trim()?.toIntOrNull()?.coerceIn(0, 100) ?: throw Dlna.Fault(402, "Invalid volume")
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max / 100f), 0)
            }
            "GetMute" -> return Dlna.values("CurrentMute", if (isMuted()) "1" else "0")
            "SetMute" -> {
                val mute = args["DesiredMute"]?.trim()?.lowercase()
                if (mute !in setOf("0", "1", "true", "false", "yes", "no")) throw Dlna.Fault(402, "Invalid mute value")
                val on = mute == "1" || mute == "true" || mute == "yes"
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
            }
            "ListPresets" -> return Dlna.values("CurrentPresetNameList", "FactoryDefaults")
            "SelectPreset" -> Unit
            else -> throw Dlna.Fault(401, "Invalid Action")
        }
        return emptyMap()
    }

    private fun volume(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max == 0) 0 else Math.round(100f * audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max)
    }

    private fun isMuted() = runCatching { audio.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(false)

    companion object {
        const val PREFERRED_PORT = 49595
    }
}
