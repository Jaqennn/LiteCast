package io.github.jqssun.airplay.renderer

/**
 * Buffers mirroring video frames while waiting for the TV foreground / HDMI handoff gate to open.
 *
 * Guarantees:
 * - Never feeds an orphaned P-frame without a preceding SPS/PPS + IDR keyframe.
 * - New IDR keyframes replace older buffered GOPs so playback starts from the freshest keyframe.
 * - If P-frames reach [maxBytes] before a new IDR arrives, further P-frames are dropped while
 *   preserving the complete SPS/PPS + IDR prefix (never evicting the IDR).
 */
class MirrorFrameGate(private val maxBytes: Int = DEFAULT_MAX_BYTES) {

    enum class State { OPEN, GATED, DROPPING }

    data class Frame(val data: ByteArray, val ptsNs: Long, val isH265: Boolean)

    private var state: State = State.OPEN
    private var paramSets: Frame? = null
    private var codecH265: Boolean? = null
    private val frames = ArrayList<Frame>()
    private var bufferedBytes = 0
    private var hasIdr = false
    private var overflowed = false

    fun currentState(): State = state

    fun armGate() {
        state = State.GATED
        clearBuffer()
    }

    fun openImmediate() {
        state = State.OPEN
        clearBuffer()
    }

    fun beginTeardown() {
        state = State.DROPPING
        clearBuffer()
    }

    /**
     * Offers an incoming frame while gated.
     * Returns true if the caller should feed directly to the decoder (state == OPEN),
     * false if the frame was buffered or dropped.
     */
    fun offer(data: ByteArray, ptsNs: Long, isH265: Boolean): Boolean {
        if (state == State.OPEN) return true
        if (state == State.DROPPING) return false

        if (codecH265 != null && codecH265 != isH265) {
            clearBuffer()
        }
        codecH265 = isH265

        val flags = classifyAnnexB(data, isH265)
        val hasParams = (flags and FLAG_PARAM_SET) != 0
        val hasIdrSlice = (flags and FLAG_IDR) != 0

        if (hasParams && !hasIdrSlice) {
            // Standalone SPS/PPS/VPS packet: keep the latest copy to prepend before a bare IDR.
            if (data.size <= maxBytes) {
                paramSets = Frame(data.copyOf(), ptsNs, isH265)
            }
            return false
        }

        if (hasIdrSlice) {
            // Fresh IDR keyframe: discard any stale GOP and start clean.
            frames.clear()
            bufferedBytes = 0
            hasIdr = false
            overflowed = false

            val prepend = if (!hasParams) paramSets else null
            if (hasParams) paramSets = null

            val totalNeeded = (prepend?.data?.size ?: 0) + data.size
            if (totalNeeded > maxBytes) {
                // Single IDR exceeds cap; wait for a smaller IDR rather than feeding partial/corrupt data.
                overflowed = true
                return false
            }
            if (prepend != null) {
                frames.add(prepend)
                bufferedBytes += prepend.data.size
            }
            frames.add(Frame(data.copyOf(), ptsNs, isH265))
            bufferedBytes += data.size
            hasIdr = true
            return false
        }

        // Non-IDR (P/B) frame: only append contiguously after a buffered IDR while under the cap.
        if (!hasIdr || overflowed) return false
        if (data.size > maxBytes - bufferedBytes) {
            // Stop appending further P-frames; keep the intact SPS/PPS + IDR + contiguous prefix.
            overflowed = true
            return false
        }
        frames.add(Frame(data.copyOf(), ptsNs, isH265))
        bufferedBytes += data.size
        return false
    }

    /** Transitions GATED -> OPEN and returns the buffered frames (always starting from SPS/PPS + IDR). */
    fun openAndDrain(): List<Frame> {
        if (state != State.GATED) {
            clearBuffer()
            return emptyList()
        }
        state = State.OPEN
        val drained = if (hasIdr) frames.toList() else emptyList()
        clearBuffer()
        return drained
    }

    internal fun bufferedByteCount(): Int = bufferedBytes

    private fun clearBuffer() {
        frames.clear()
        paramSets = null
        codecH265 = null
        bufferedBytes = 0
        hasIdr = false
        overflowed = false
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 4 * 1024 * 1024
        internal const val FLAG_PARAM_SET = 1
        internal const val FLAG_IDR = 2

        /** Scans Annex-B NAL start codes (3-byte or 4-byte) and returns bitmask of param-set / IDR flags. */
        internal fun classifyAnnexB(data: ByteArray, isH265: Boolean): Int {
            if (data.size < 4) return 0
            var flags = 0
            var i = 0
            val n = data.size
            while (i <= n - 4) {
                val nalStart = when {
                    data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                        data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte() -> i + 4
                    data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                        data[i + 2] == 1.toByte() -> i + 3
                    else -> -1
                }
                if (nalStart >= 0 && nalStart < n) {
                    if (isH265) {
                        val type = (data[nalStart].toInt() shr 1) and 0x3F
                        if (type == 32 || type == 33 || type == 34) flags = flags or FLAG_PARAM_SET
                        else if (type == 19 || type == 20 || type == 21) flags = flags or FLAG_IDR
                    } else {
                        val type = data[nalStart].toInt() and 0x1F
                        if (type == 7 || type == 8) flags = flags or FLAG_PARAM_SET
                        else if (type == 5) flags = flags or FLAG_IDR
                    }
                    i = nalStart + 1
                } else {
                    i++
                }
            }
            return flags
        }
    }
}
