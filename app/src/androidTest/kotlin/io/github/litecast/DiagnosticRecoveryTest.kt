/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast

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
}
