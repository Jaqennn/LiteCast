/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.litecast.diagnostics.Diagnostics
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CI invokes each method separately, force-stopping the process between save and reload. */
@RunWith(AndroidJUnit4::class)
class DiagnosticRecoveryTest {
    @Before fun requireSeparateProcessScenario() {
        // These phases intentionally span processes; a normal connected test run skips them.
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryTest") == "true")
    }

    @Test fun prepareCrashRecord() {
        Diagnostics.record("LC-JAVA-CRASH", "recovery-test", "Recovery test: original cause retained", true)
        assertTrue(Diagnostics.needsRecovery())
    }

    @Test fun verifyCrashRecordAfterRestart() {
        val report = checkNotNull(Diagnostics.reports.value)
        assertEquals("LC-JAVA-CRASH", report.code)
        assertEquals("recovery-test", report.stage)
        assertTrue(report.detail.contains("original cause retained"))
        assertTrue(Diagnostics.needsRecovery())
        Diagnostics.acknowledge(report)
        // Viewing an error must not silently resume the receiver that crashed.
        assertTrue(Diagnostics.needsRecovery())
        Diagnostics.allowRetry()
        assertFalse(Diagnostics.needsRecovery())
    }

    @Test fun prepareCrashWithNativeTrail() {
        val report = Diagnostics.record("LC-JAVA-CRASH", "player binding", "Original player failure", true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("recovery-fixture", Context.MODE_PRIVATE).edit()
            .putString("crash_id", report.id).putLong("crash_time", report.time).commit()
        java.io.File(Diagnostics.nativeTracePath()).writeText("1730000000.001 tid=123 request.pair-verify\n")
    }

    @Test fun verifyEnrichmentPreservesCrashIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = context.getSharedPreferences("recovery-fixture", Context.MODE_PRIVATE)
        val report = checkNotNull(Diagnostics.reports.value)
        assertEquals(expected.getString("crash_id", null), report.id)
        assertEquals(expected.getLong("crash_time", -1), report.time)
        assertTrue(report.detail.contains("Original player failure"))
        assertTrue(report.detail.contains("request.pair-verify"))
        assertTrue(Diagnostics.needsRecovery())
        Diagnostics.acknowledge(report)
        Diagnostics.allowRetry()
        assertFalse(Diagnostics.needsAttention(report))
    }

    @Test fun verifyRetryAndAcknowledgementAfterRestart() {
        assertFalse(Diagnostics.needsRecovery())
        assertFalse(Diagnostics.needsAttention(checkNotNull(Diagnostics.reports.value)))
    }

    @Test fun prepareInterruptedStartup() {
        Diagnostics.beginStartup("native-startup-test")
    }

    @Test fun verifyInterruptedStartupAfterRestart() {
        val report = checkNotNull(Diagnostics.reports.value)
        assertEquals("LC-START-INTERRUPTED", report.code)
        assertEquals("native-startup-test", report.stage)
        assertTrue(report.detail.contains("No crash stack"))
        assertTrue(Diagnostics.needsRecovery())
        Diagnostics.allowRetry()
    }

    @Test fun prepareNativeStageTrail() {
        Diagnostics.record("LC-TEST-COMPLETE", "native stage fixture", "Previous recovery checks completed")
        java.io.File(Diagnostics.nativeTracePath()).writeText(
            "1730000000.000 tid=123 connection.accepted\n1730000000.001 tid=123 request.pair-verify\n")
    }

    @Test fun verifyNativeStageTrailAfterRestart() {
        val report = checkNotNull(Diagnostics.reports.value)
        assertEquals("LC-AIRPLAY-LAST-RUN", report.code)
        assertTrue(report.detail.contains("request.pair-verify"))
        assertTrue(report.detail.contains("not a crash stack"))
        assertFalse(Diagnostics.needsRecovery())
        assertFalse(Diagnostics.needsAttention(report))
    }
}
