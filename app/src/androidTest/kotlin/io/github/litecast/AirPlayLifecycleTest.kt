package io.github.litecast

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jqssun.airplay.bridge.NativeBridge
import io.github.jqssun.airplay.bridge.RaopCallbackHandler
import io.github.jqssun.airplay.bridge.LogListener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy
import java.net.Socket

/** Real native HTTP/RTSP server, threads and JNI callbacks on each Android version. */
@RunWith(AndroidJUnit4::class)
class AirPlayLifecycleTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun callback(): RaopCallbackHandler = Proxy.newProxyInstance(
        RaopCallbackHandler::class.java.classLoader,
        arrayOf(RaopCallbackHandler::class.java, LogListener::class.java)
    ) { _, method, _ -> if (method.returnType == Float::class.javaPrimitiveType) -20f else null } as RaopCallbackHandler

    @Test fun repeatedDiscoveryDisconnectAndShutdown() {
        NativeBridge.ensureLoaded()
        val trace = File(context.filesDir, "airplay-lifecycle-test.txt")
        trace.delete()
        NativeBridge.nativeSetDiagnosticPath(trace.absolutePath)
        repeat(8) {
            val handle = NativeBridge.nativeInit(callback(), byteArrayOf(2, 1, 2, 3, 4, 5),
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
