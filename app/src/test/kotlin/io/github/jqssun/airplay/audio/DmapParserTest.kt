package io.github.jqssun.airplay.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DmapParserTest {

    @Test
    fun parsesStandardMetadata() {
        val title = "LiteCast".toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(8 + title.size + 12).order(ByteOrder.BIG_ENDIAN)
        buf.put("minm".toByteArray(Charsets.US_ASCII))
        buf.putInt(title.size)
        buf.put(title)
        buf.put("astm".toByteArray(Charsets.US_ASCII))
        buf.putInt(4)
        buf.putInt(180_000)

        val parsed = DmapParser.parse(buf.array())
        assertEquals("LiteCast", parsed["minm"])
        assertEquals(180_000L, parsed["astm"])
    }

    @Test
    fun deeplyNestedContainersDoNotStackOverflow() {
        val depth = 2048
        val packet = ByteArray(depth * 8)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        for (i in 0 until depth) {
            buf.put("mlit".toByteArray(Charsets.US_ASCII))
            buf.putInt((depth - 1 - i) * 8)
        }
        val parsed = DmapParser.parse(packet)
        assertTrue(parsed.containsKey("mlit"))
    }
}
