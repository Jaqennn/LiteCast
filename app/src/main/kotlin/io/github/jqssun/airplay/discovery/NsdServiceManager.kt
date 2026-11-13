package io.github.jqssun.airplay.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log

data class MdnsService(val name: String, val type: String, val port: Int, val txt: Map<String, String>)

class NsdServiceManager(private val ctx: Context) {

    private val nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val handler = Handler(Looper.getMainLooper())
    private var multicastLock: WifiManager.MulticastLock? = null
    private val registrations = mutableListOf<NsdManager.RegistrationListener>()
    private var advertised: List<MdnsService> = emptyList()
    private var refreshIntervalMs = 0L
    private var reregistering = false
    private var pendingUnregisters = 0
    private val refreshRunnable = Runnable { _refresh() }
    private val reregisterTimeout = Runnable { _finishReregister() }

    fun acquireMulticastLock() {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("airplay_mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    fun register(services: List<MdnsService>) {
        advertised = services
        services.forEach(::_register)
    }

    /**
     * Periodically unregister and register again so a stale NsdManager advertisement
     * (common on older TV firmwares that do not re-announce on their own) does not
     * leave the TV invisible. DLNA already does this with SSDP NOTIFY.
     */
    fun startPeriodicRefresh(intervalMs: Long) {
        refreshIntervalMs = intervalMs
        _scheduleRefresh()
    }

    private fun _scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable)
        if (refreshIntervalMs > 0) handler.postDelayed(refreshRunnable, refreshIntervalMs)
    }

    private fun _refresh() {
        if (advertised.isEmpty() || reregistering) {
            _scheduleRefresh()
            return
        }
        val old = registrations.toList()
        registrations.clear()
        if (old.isEmpty()) {
            advertised.forEach(::_register)
            _scheduleRefresh()
            return
        }
        reregistering = true
        pendingUnregisters = old.size
        old.forEach {
            try {
                nsdManager.unregisterService(it)
            } catch (_: Exception) {
                pendingUnregisters--
            }
        }
        if (pendingUnregisters <= 0) {
            _finishReregister()
            return
        }
        // NsdManager callbacks can stall on some TV firmwares; don't stay unadvertised
        handler.postDelayed(reregisterTimeout, REREGISTER_TIMEOUT_MS)
    }

    private fun _finishReregister() {
        if (!reregistering) return
        reregistering = false
        pendingUnregisters = 0
        handler.removeCallbacks(reregisterTimeout)
        advertised.forEach(::_register)
        _scheduleRefresh()
    }

    private fun _register(s: MdnsService) {
        val info = NsdServiceInfo().apply {
            serviceName = s.name
            serviceType = s.type
            port = s.port
            s.txt.forEach { (k, v) -> setAttribute(k, v) }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "${s.type} registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "${s.type} registration failed: $code")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "${s.type} unregistered")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "${s.type} unregister failed: $code")
            }
        }
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        registrations += listener
    }

    fun release() {
        handler.removeCallbacks(refreshRunnable)
        handler.removeCallbacks(reregisterTimeout)
        reregistering = false
        pendingUnregisters = 0
        refreshIntervalMs = 0
        advertised = emptyList()
        registrations.forEach {
            try { nsdManager.unregisterService(it) } catch (_: Exception) {}
        }
        registrations.clear()
        multicastLock?.release()
        multicastLock = null
    }

    companion object {
        private const val TAG = "NsdServiceManager"
        private const val REREGISTER_TIMEOUT_MS = 2_000L
    }
}
