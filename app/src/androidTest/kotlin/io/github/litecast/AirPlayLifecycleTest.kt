package io.github.litecast

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import io.github.jqssun.airplay.BootReceiver
import io.github.jqssun.airplay.service.AirPlayService
import org.junit.Before
import org.junit.After
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jqssun.airplay.bridge.NativeBridge
import io.github.jqssun.airplay.bridge.RaopCallbackHandler
import io.github.jqssun.airplay.bridge.LogListener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.Socket

/** Real native HTTP/RTSP server, threads and JNI callbacks on each Android version. */
@RunWith(AndroidJUnit4::class)
class AirPlayLifecycleTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var receiverState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

    @Before fun isolateFromBootReceiver() {
        // A delayed emulator BOOT_COMPLETED must not start the production receiver
        // while this test owns the process-wide native diagnostic sink.
        val receiver = ComponentName(context, BootReceiver::class.java)
        receiverState = context.packageManager.getComponentEnabledSetting(receiver)
        context.packageManager.setComponentEnabledSetting(receiver,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            context.stopService(Intent(context, AirPlayService::class.java))
        }
        instrumentation.waitForIdleSync()
    }

    @After fun restoreBootReceiver() {
        NativeBridge.nativeSetDiagnosticPath(
            io.github.litecast.diagnostics.Diagnostics.nativeTracePath())
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, BootReceiver::class.java), receiverState,
            PackageManager.DONT_KILL_APP)
    }

    // Use concrete callbacks, as the service does. ART CheckJNI handles dynamic
    // proxy method IDs differently across releases; proxies obscure this native test.
    private class Callbacks : RaopCallbackHandler, LogListener {
        override fun onVideoData(data: ByteArray, ntpTimeNs: Long, isH265: Boolean) {}
        override fun onAudioFormat(ct: Int, spf: Int, usingScreen: Boolean) {}
        override fun onVideoSize(srcW: Float, srcH: Float, w: Float, h: Float) {}
        override fun onClientVolume(db: Float) {}
        override fun onClientVolumeQuery(): Float = -20f
        override fun onAudioTeardown() {}
        override fun onConnectionInit() {}
        override fun onConnectionDestroy() {}
        override fun onConnectionReset(reason: Int) {}
        override fun onDisplayPin(pin: String) {}
        override fun onMetadata(data: ByteArray) {}
        override fun onCoverArt(data: ByteArray) {}
        override fun onProgress(start: Long, curr: Long, end: Long) {}
        override fun onDacpId(dacpId: String, activeRemote: String) {}
        override fun onMirrorRunning(running: Boolean) {}
        override fun onVideoPlay(location: String, startPositionSeconds: Float) {}
        override fun onVideoScrub(positionSeconds: Float) {}
        override fun onVideoRate(rate: Float) {}
        override fun onVideoStop() {}
        override fun onVideoSessionPoll() {}
        override fun onLog(msg: String) {}
    }

    @Test fun repeatedDiscoveryDisconnectAndShutdown() {
        NativeBridge.ensureLoaded()
        val trace = File(context.filesDir, "airplay-lifecycle-test.txt")
        trace.delete()
        NativeBridge.nativeSetDiagnosticPath(trace.absolutePath)
        repeat(8) {
            val handle = NativeBridge.nativeInit(Callbacks(), byteArrayOf(2, 1, 2, 3, 4, 5),
                "Lifecycle test", File(context.filesDir, "lifecycle-test.key").absolutePath, false, false)
            assertTrue("Native initialization", handle != 0L)
            try {
                NativeBridge.nativeSetHlsEnabled(handle, true)
                val port = NativeBridge.nativeStart(handle, 0)
                assertTrue(port > 0)
                for (path in listOf("/info", "/info?txtAirPlay?txtRAOP", "/server-info")) {
                    Socket("127.0.0.1", port).use { socket ->
                        socket.soTimeout = 5000
                        val request = if (path == "/server-info")
                            "GET $path HTTP/1.1\r\nX-Apple-Session-ID: lifecycle-test\r\nContent-Length: 0\r\n\r\n"
                        else "GET $path RTSP/1.0\r\n${if ('?' in path) "" else "CSeq: 1\r\n"}Content-Length: 0\r\n\r\n"
                        socket.getOutputStream().write(request.toByteArray())
                        val input = socket.getInputStream().bufferedReader()
                        assertTrue("Response to $path", input.readLine()?.contains("200") == true)
                    }
                }
                // A partial request and an abrupt disconnect must survive server shutdown.
                Socket("127.0.0.1", port).use { socket ->
                    socket.getOutputStream().write("GET /info RTSP/1.0\r\nCSeq:".toByteArray())
                }
                Socket("127.0.0.1", port).use { socket ->
                    socket.getOutputStream().write("GET /info RTSP/1.0\r\nCSeq:".toByteArray())
                    Thread.sleep(50)
                    NativeBridge.nativeStop(handle)
                    NativeBridge.nativeStop(handle)
                }
            } finally {
                NativeBridge.nativeDestroy(handle)
            }
        }
        val trail = trace.readText()
        assertTrue(trail.contains("connection.init.ready"))
        assertTrue(trail.contains("request.info"))
        assertTrue(trail.contains("server.destroy.done"))
        assertFalse(trail.contains("lifecycle-test.key"))
    }
}
