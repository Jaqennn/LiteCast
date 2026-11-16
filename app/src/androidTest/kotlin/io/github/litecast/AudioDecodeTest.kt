package io.github.litecast

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jqssun.airplay.bridge.NativeBridge
import io.github.jqssun.airplay.renderer.AudioConfig
import io.github.jqssun.airplay.renderer.forDevice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteOrder
import kotlin.math.sin

/** Real encoded audio -> bundled FFmpeg -> PCM on API 21/22/23/31. No network/iPhone emulation. */
@RunWith(AndroidJUnit4::class)
class AudioDecodeTest {
    private external fun decodePackets(ct: Int, spf: Int, packets: Array<ByteArray>): Int

    @Test fun aacLcProducesPcm() = roundTripAacLc()

    @Test fun aacEldProducesPcm() {
        NativeBridge.ensureLoaded()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        for (spf in listOf(480, 512)) {
            // Fixed AirPlay-format packets avoid platform encoders that silently choose
            // another profile or produce a different AAC-ELD configuration.
            val packets = assets.open("aac-eld-$spf.hex").bufferedReader().useLines { lines ->
                lines.map { line ->
                    ByteArray(line.length / 2) { i ->
                        line.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                    }
                }.toList().toTypedArray()
            }
            assertTrue("decoded non-silent PCM ct=8 spf=$spf", decodePackets(8, spf, packets) > 1000)
        }
    }

    @Test fun malformedAndUnknownAudioFailWithoutCrashing() {
        NativeBridge.ensureLoaded()
        assertEquals(-1, decodePackets(999, 480, arrayOf(byteArrayOf(0))))
        assertEquals(0, decodePackets(8, 480, arrayOf(byteArrayOf(0))))
        val policy = AudioConfig().forDevice(android.os.Build.VERSION.SDK_INT)
        if (android.os.Build.VERSION.SDK_INT <= 22) assertTrue(policy.forceSwAac && policy.forceSwAlac)
    }

    private fun roundTripAacLc() {
        NativeBridge.ensureLoaded()
        val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
        val packets = ArrayList<ByteArray>()
        try {
            encoder.configure(MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            var sentFrames = 0
            var sentEos = false
            var receivedEos = false
            val info = MediaCodec.BufferInfo()
            val deadline = android.os.SystemClock.elapsedRealtime() + 15000
            while (!receivedEos && android.os.SystemClock.elapsedRealtime() < deadline) {
                if (!sentEos) {
                    val input = encoder.dequeueInputBuffer(1000)
                    if (input >= 0) {
                        val buffer = encoder.getInputBuffer(input)!!.order(ByteOrder.nativeOrder())
                        buffer.clear()
                        val frames = minOf(buffer.remaining() / 4, 22050 - sentFrames)
                        repeat(frames) { offset ->
                            val value = (sin((sentFrames + offset) * 2 * Math.PI * 440 / 44100) * 8000).toInt().toShort()
                            buffer.putShort(value); buffer.putShort(value)
                        }
                        sentEos = frames == 0
                        encoder.queueInputBuffer(input, 0, frames * 4, sentFrames * 1000000L / 44100,
                            if (sentEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        sentFrames += frames
                    }
                }
                val output = encoder.dequeueOutputBuffer(info, 1000)
                if (output >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val buffer = encoder.getOutputBuffer(output)!!
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        packets.add(ByteArray(info.size).also { buffer.get(it) })
                    }
                    receivedEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(output, false)
                }
            }
            assertTrue("encoder completed", receivedEos)
            assertTrue("encoded packets", packets.size > 5)
            assertTrue("decoded non-silent AAC-LC PCM", decodePackets(4, 1024, packets.toTypedArray()) > 1000)
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
        }
    }
}
