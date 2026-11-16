package io.github.jqssun.airplay.renderer

/**
 * mirrors native AudioConfig struct; defaults must match preference defaults so a
 * freshly-constructed AudioRenderer is sane
 */
data class AudioConfig(
    val cushionMs: Int = 0,           // 0 = adaptive tuner, otherwise fixed cushion ms
    val percentilePct: Int = 95,
    val oboeBufferFrames: Int = 0,    // 0 = auto (two bursts)
    val forceSwAlac: Boolean = false,
    val forceSwAac: Boolean = false,
    val realtimePriority: Boolean = true,
    val lowLatency: Boolean = true,
    val benchmarkLog: Boolean = false,
)

// Android 5.x NDK MediaCodec can return a wrapper with a null internal codec.
// Avoid that API entirely for audio on API 21/22, including ALAC fallback probing.
internal fun AudioConfig.forDevice(api: Int): AudioConfig =
    if (api <= 22) copy(forceSwAac = true, forceSwAlac = true) else this
