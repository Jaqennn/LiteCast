package io.github.jqssun.airplay.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Parses DMAP (iTunes metadata) binary TLV format into a tag->value map. */
object DmapParser {

    // tags whose payload is an integer (1/2/4/8 bytes)
    private val INT_TAGS = setOf(
        "astm", // duration ms
        "astn", // track number
        "asdk", // data kind
        "asts", // ???
        "miid", // item id
        "mcti", // container item id
        "mper", // persistent id
        "asai", // album id
        "asri", // artist id
        "asci", // composer id
        "asgi", // genre id
    )

    // tags that are containers (contain nested dmap items)
    private val CONTAINER_TAGS = setOf("mlit", "mcon", "mlcl")
    private const val MAX_DEPTH = 16

    fun parse(data: ByteArray): Map<String, Any> = parseRange(data, 0, data.size, 0)

    private fun parseRange(data: ByteArray, start: Int, end: Int, depth: Int): Map<String, Any> {
        if (depth >= MAX_DEPTH || start >= end) return emptyMap()
        val result = mutableMapOf<String, Any>()
        val buf = ByteBuffer.wrap(data, start, end - start).order(ByteOrder.BIG_ENDIAN)
        while (buf.remaining() >= 8) {
            val tagStart = buf.position()
            val tag = String(data, tagStart, 4, Charsets.US_ASCII)
            buf.position(tagStart + 4)
            val len = buf.int
            if (len < 0 || len > buf.remaining()) break
            val payloadStart = buf.position()
            val payloadEnd = payloadStart + len
            buf.position(payloadEnd)
            result[tag] = when {
                tag in CONTAINER_TAGS -> parseRange(data, payloadStart, payloadEnd, depth + 1)
                tag in INT_TAGS -> _readInt(data, payloadStart, len)
                else -> String(data, payloadStart, len, Charsets.UTF_8)
            }
        }
        return result
    }

    private fun _readInt(data: ByteArray, offset: Int, len: Int): Long {
        var v = 0L
        for (i in 0 until len) v = (v shl 8) or (data[offset + i].toLong() and 0xFF)
        return v
    }
}
