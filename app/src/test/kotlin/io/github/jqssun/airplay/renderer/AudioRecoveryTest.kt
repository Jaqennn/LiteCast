package io.github.jqssun.airplay.renderer

import org.junit.Assert.*
import org.junit.Test

class AudioRecoveryTest {
    @Test fun legacyPolicyOverridesOldSavedPreferencesWithoutChangingModernConfiguration() {
        val original = AudioConfig(cushionMs = 123, oboeBufferFrames = 256)
        for (api in listOf(21, 22)) {
            val safe = original.forDevice(api)
            assertTrue(safe.forceSwAac)
            assertTrue(safe.forceSwAlac)
            assertEquals(123, safe.cushionMs)
            assertEquals(256, safe.oboeBufferFrames)
        }
        for (api in listOf(23, 31, 37)) assertSame(original, original.forDevice(api))
        assertFalse(original.forceSwAac)
    }

    @Test fun retryRecoversStaticScreenWithoutNewFramesAndPreservesOrder() {
        val tasks = ArrayDeque<() -> Unit>()
        var attempts = 0
        var decoded = emptyList<String>()
        val recovery = VideoStartRecovery<String>(
            schedule = { delay, task -> assertEquals(300L, delay); tasks.add(task) },
            recover = { frames ->
                attempts++
                if (attempts == 1) error("decoder busy")
                decoded = frames
            }, failedAttempt = {}, exhausted = { fail("should recover") })
        recovery.begin("key", 3)
        recovery.append("next", 4)
        tasks.removeFirst().invoke()
        tasks.removeFirst().invoke()
        assertEquals(listOf("key", "next"), decoded)
        assertFalse(recovery.pending)
    }

    @Test fun stoppedSessionCannotBeRevivedByAnOldTimer() {
        val tasks = ArrayDeque<() -> Unit>()
        var decoded = emptyList<String>()
        val recovery = VideoStartRecovery<String>(
            schedule = { _, task -> tasks.add(task) }, recover = { decoded = it },
            failedAttempt = {}, exhausted = { fail("should not exhaust") })
        recovery.begin("old", 3)
        recovery.cancel()
        recovery.begin("new", 3)
        tasks.removeFirst().invoke()
        assertTrue(decoded.isEmpty())
        tasks.removeFirst().invoke()
        assertEquals(listOf("new"), decoded)
    }

    @Test fun failingDecoderHasBoundedRetriesAndBuffer() {
        val tasks = ArrayDeque<() -> Unit>()
        var attempts = 0
        var failures = 0
        val recovery = VideoStartRecovery<String>(
            schedule = { _, task -> tasks.add(task) }, recover = { attempts++; error("busy") },
            failedAttempt = {}, exhausted = { failures++ }, maxBytes = 10)
        recovery.begin("key", 3)
        repeat(3) { tasks.removeFirst().invoke() }
        assertEquals(3, attempts)
        assertEquals(1, failures)
        assertFalse(recovery.pending)
        assertTrue(tasks.isEmpty())
        recovery.begin("key", 3)
        recovery.append("oversized", 9)
        tasks.removeFirst().invoke()
        assertEquals(3, attempts)
        assertEquals(2, failures)
        assertFalse(recovery.pending)
    }
}
