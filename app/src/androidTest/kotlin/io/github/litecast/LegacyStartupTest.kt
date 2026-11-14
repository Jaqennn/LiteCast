/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jqssun.airplay.bridge.NativeBridge
import io.github.jqssun.airplay.service.AirPlayService
import io.github.litecast.tv.HomeActivity
import io.github.litecast.tv.DiagnosticActivity
import io.github.litecast.diagnostics.Diagnostics
import android.graphics.Bitmap
import org.junit.Assert.assertFalse
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/** Runs in a real Android process, including old bionic, instead of JVM shadows. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LegacyStartupTest {
    @Test fun aNativeLibraryLoadsAlongsideOldSystemCrypto() {
        if (Build.VERSION.SDK_INT <= 22) {
            val path = if (Build.SUPPORTED_ABIS.first().contains("64")) "/system/lib64/libcrypto.so" else "/system/lib/libcrypto.so"
            if (File(path).exists()) System.load(path)
        }
        NativeBridge.ensureLoaded()
        // Exercise a JNI call as well as dlopen.
        NativeBridge.nativeSetDefaultStreamValues(48000, 192)
    }

    @Test fun cHomeActivityStartsOnTheActualSystem() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        assertFalse(activity.isFinishing)
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun bDiagnosticPageOpensWithoutStartingTheReceiver() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        Diagnostics.record("LC-NATIVE-LOAD", "AirPlay native load",
            UnsatisfiedLinkError("cannot locate symbol EVP_CIPHER_CTX_reset referenced by libairplay_native.so"))
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, DiagnosticActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        assertFalse(activity.isFinishing)
        @Suppress("DEPRECATION")
        val services = (instrumentation.targetContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
        assertFalse(services.any { it.service.className == AirPlayService::class.java.name })
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.filesDir, "diagnostic-preview.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        instrumentation.runOnMainSync { activity.finish() }
    }
}
