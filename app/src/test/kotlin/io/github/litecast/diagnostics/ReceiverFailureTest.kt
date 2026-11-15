/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast.diagnostics

import android.opengl.EGL14
import android.opengl.EGLSurface
import io.github.jqssun.airplay.service.AirPlayService
import io.github.litecast.dlna.DlnaRenderer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29])
class ReceiverFailureTest {
    @Before fun provideEglSentinel() {
        val app = RuntimeEnvironment.getApplication()
        // A real install grants this signature permission from AndroidX's merged manifest.
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        // Robolectric stubs EGL's native class initializer. This test doesn't render frames,
        // but VideoPipeline still needs the non-null sentinel supplied by a real device.
        ReflectionHelpers.setStaticField(EGL14::class.java, "EGL_NO_SURFACE",
            ReflectionHelpers.callConstructor<EGLSurface>(EGLSurface::class.java,
                ReflectionHelpers.ClassParameter.from(java.lang.Long.TYPE, 0L)))
    }

    class MissingNativeService : AirPlayService() {
        override fun startAirplay(effectiveName: String) {
            throw UnsatisfiedLinkError("cannot locate EVP_CIPHER_CTX_reset")
        }
    }

    @Test fun nativeFailureKeepsDlnaAndStopStillReleasesTheReceiver() {
        val controller = Robolectric.buildService(MissingNativeService::class.java).create()
        val service = controller.get()
        try {
            service.startServer()
            assertEquals(AirPlayService.ServerState.ERROR, service.serverState.value)
            assertTrue(service.isReceiverActive)
            assertEquals("LC-NATIVE-LOAD", Diagnostics.reports.value?.code)
            assertTrue(Diagnostics.reports.value!!.detail.contains("EVP_CIPHER_CTX_reset"))
            val firstLock = ShadowPowerManager.getLatestWakeLock()
            assertTrue(firstLock.isHeld)
            service.setDlnaEnabled(false)
            assertFalse(service.isReceiverActive)
            assertFalse(firstLock.isHeld)
            service.setDlnaEnabled(true)
            assertTrue(service.isReceiverActive)
            assertEquals(AirPlayService.ServerState.ERROR, service.serverState.value)
            val restartedLock = ShadowPowerManager.getLatestWakeLock()
            assertTrue(restartedLock.isHeld)
            service.stopServer()
            assertFalse(service.isReceiverActive)
            assertFalse(restartedLock.isHeld)
            assertEquals(DlnaRenderer.Status.STOPPED, service.dlnaStatus.value)
        } finally {
            controller.destroy()
        }
    }
}
