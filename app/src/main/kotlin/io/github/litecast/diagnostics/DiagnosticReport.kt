/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Deliberately contains no URLs, device identifiers, cast titles, credentials or network log. */
data class DiagnosticReport(
    val id: String,
    val time: Long,
    val code: String,
    val stage: String,
    val device: String,
    val detail: String,
    val recoveryRequired: Boolean = false,
) {
    fun text(): String = "LiteCast $code / $id\n" +
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date(time)) +
        "\n$device\nStage: $stage\n\n$detail"

    companion object {
        private const val LIMIT = 16_000
        // Error messages can include signed playback URLs, local addresses and pairing data.
        fun redact(value: String): String = value.take(64_000)
            .replace(Regex("(?i)\\b(?:https?|rtsp|file)://[^\\s<>\\\"]+"), "[URL]")
            .replace(Regex("(?i)\\b(?:authorization|cookie|password|token|secret|pin)\\s*[:=][^\\r\\n]*"), "[private data]")
            .replace(Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"), "[IP]")
            .replace(Regex("(?i)\\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\\b"), "[MAC]")
            .take(LIMIT)

        /** Bound recursion and output even for malformed/cyclic exception chains. */
        fun describe(error: Throwable): String {
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
            val text = StringBuilder()
            var cause: Throwable? = error
            while (cause != null && seen.size < 8 && seen.add(cause)) {
                if (text.isNotEmpty()) text.append("Caused by: ")
                text.append(cause.javaClass.name).append(": ").append(cause.message.orEmpty().take(2000)).append('\n')
                cause.stackTrace.take(35).forEach { text.append("  at ").append(it).append('\n') }
                cause = cause.cause
            }
            return redact(text.toString())
        }
    }
}
