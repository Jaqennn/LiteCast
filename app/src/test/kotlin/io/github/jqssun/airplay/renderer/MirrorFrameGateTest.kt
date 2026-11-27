package io.github.jqssun.airplay.renderer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorFrameGateTest {

    private fun nalu4(header: Int, payloadSize: Int = 4, fill: Byte = 0x11): ByteArray {
        val out = ByteArray(5 + payloadSize)
        out[0] = 0; out[1] = 0; out[2] = 0; out[3] = 1
        out[4] = header.toByte()
        for (i in 5 until out.size) out[i] = fill
        return out
    }

    private fun nalu3(header: Int, payloadSize: Int = 4, fill: Byte = 0x22): ByteArray {
        val out = ByteArray(4 + payloadSize)
        out[0] = 0; out[1] = 0; out[2] = 1
        out[3] = header.toByte()
        for (i in 4 until out.size) out[i] = fill
        return out
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, pos, p.size)
            pos += p.size
        }
        return out
    }

    @Test
    fun openStatePassesFramesDirectly() {
        val gate = MirrorFrameGate()
        assertEquals(MirrorFrameGate.State.OPEN, gate.currentState())
        assertTrue(gate.offer(nalu4(0x65), 100L, isH265 = false))
    }

    @Test
    fun gatedModeDropsNonIdrBeforeFirstKeyframe() {
        val gate = MirrorFrameGate()
        gate.armGate()
        assertFalse(gate.offer(nalu4(0x41), 100L, isH265 = false)) // H.264 P-frame
        assertFalse(gate.offer(nalu4(0x41), 200L, isH265 = false))
        assertTrue(gate.openAndDrain().isEmpty())
        assertEquals(MirrorFrameGate.State.OPEN, gate.currentState())
    }

    @Test
    fun gatedModePrependsStandaloneSpsPpsBeforeBareIdr() {
        val gate = MirrorFrameGate()
        gate.armGate()
        val spsPps = concat(nalu4(0x67, 8, 1), nalu4(0x68, 4, 2))
        val idr = nalu4(0x65, 16, 3)
        val p1 = nalu4(0x41, 10, 4)

        assertFalse(gate.offer(spsPps, 10L, isH265 = false))
        assertFalse(gate.offer(idr, 10L, isH265 = false))
        assertFalse(gate.offer(p1, 20L, isH265 = false))

        val drained = gate.openAndDrain()
        assertEquals(3, drained.size)
        assertArrayEquals(spsPps, drained[0].data)
        assertArrayEquals(idr, drained[1].data)
        assertArrayEquals(p1, drained[2].data)
    }

    @Test
    fun newerIdrReplacesStaleGop() {
        val gate = MirrorFrameGate()
        gate.armGate()
        val gop1Idr = concat(nalu4(0x67, 4, 1), nalu4(0x68, 4, 2), nalu4(0x65, 16, 3))
        val gop1P = nalu4(0x41, 16, 4)
        val gop2Idr = concat(nalu4(0x67, 4, 5), nalu4(0x68, 4, 6), nalu4(0x65, 20, 7))
        val gop2P = nalu4(0x41, 12, 8)

        gate.offer(gop1Idr, 10L, isH265 = false)
        gate.offer(gop1P, 20L, isH265 = false)
        gate.offer(gop2Idr, 30L, isH265 = false)
        gate.offer(gop2P, 40L, isH265 = false)

        val drained = gate.openAndDrain()
        assertEquals(2, drained.size)
        assertArrayEquals(gop2Idr, drained[0].data)
        assertArrayEquals(gop2P, drained[1].data)
    }

    @Test
    fun maxBytesCapPreservesIntactIdrPrefixAndStopsAppendingPFrames() {
        // Small cap to verify 4MB cap behavior deterministically:
        // SPS+PPS+IDR = 40 bytes, P1 = 30 bytes (fits: 70 <= 100), P2 = 50 bytes (overflows), P3 = 10 bytes (must also drop!)
        val gate = MirrorFrameGate(maxBytes = 100)
        gate.armGate()
        val idr = concat(nalu4(0x67, 5, 1), nalu4(0x68, 5, 2), nalu4(0x65, 15, 3)) // 10+10+20 = 40 bytes
        val p1 = nalu4(0x41, 25, 4) // 30 bytes -> total 70
        val p2 = nalu4(0x41, 45, 5) // 50 bytes -> would exceed 100
        val p3 = nalu4(0x41, 5, 6)  // 10 bytes -> would fit, but must NOT be appended after p2 dropped!

        gate.offer(idr, 1L, isH265 = false)
        gate.offer(p1, 2L, isH265 = false)
        gate.offer(p2, 3L, isH265 = false)
        gate.offer(p3, 4L, isH265 = false)

        val drained = gate.openAndDrain()
        assertEquals(2, drained.size)
        assertArrayEquals(idr, drained[0].data)
        assertArrayEquals(p1, drained[1].data)
    }

    @Test
    fun hevcVpsSpsPpsAndIdrWith3ByteStartCodes() {
        val gate = MirrorFrameGate()
        gate.armGate()
        // HEVC NAL header byte0 = (nalType << 1): VPS=32 (0x40), SPS=33 (0x42), PPS=34 (0x44), IDR_W_RADL=19 (0x26), TRAIL_R=1 (0x02)
        val params = concat(nalu3(32 shl 1, 4, 1), nalu3(33 shl 1, 4, 2), nalu3(34 shl 1, 4, 3))
        val idr = nalu3(19 shl 1, 16, 4)
        val trail = nalu3(1 shl 1, 12, 5)

        gate.offer(trail, 1L, isH265 = true) // dropped before IDR
        gate.offer(params, 2L, isH265 = true)
        gate.offer(idr, 2L, isH265 = true)
        gate.offer(trail, 3L, isH265 = true)

        val drained = gate.openAndDrain()
        assertEquals(3, drained.size)
        assertTrue(drained.all { it.isH265 })
        assertArrayEquals(params, drained[0].data)
        assertArrayEquals(idr, drained[1].data)
        assertArrayEquals(trail, drained[2].data)
    }

    @Test
    fun teardownStateDropsAllFramesAndDrainsEmpty() {
        val gate = MirrorFrameGate()
        gate.armGate()
        gate.offer(concat(nalu4(0x67, 4), nalu4(0x65, 16)), 1L, isH265 = false)
        gate.beginTeardown()
        assertEquals(MirrorFrameGate.State.DROPPING, gate.currentState())
        assertFalse(gate.offer(concat(nalu4(0x67, 4), nalu4(0x65, 16)), 2L, isH265 = false))
        assertTrue(gate.openAndDrain().isEmpty())
        assertEquals(MirrorFrameGate.State.DROPPING, gate.currentState())
    }
}
