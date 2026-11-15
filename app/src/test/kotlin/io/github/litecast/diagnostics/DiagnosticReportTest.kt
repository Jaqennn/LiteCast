/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportTest {
    @Test fun reportKeepsMissingSymbolButRemovesPrivatePayloads() {
        val error = UnsatisfiedLinkError("cannot locate EVP_CIPHER_CTX_reset in libairplay_native.so\n" +
            "https://media.example/video?token=secret\nCookie: session=private\n192.168.1.55 aa:bb:cc:dd:ee:ff")
        val text = DiagnosticReport.describe(error)
        assertTrue(text.contains("EVP_CIPHER_CTX_reset"))
        assertTrue(text.contains("UnsatisfiedLinkError"))
        for (privateText in listOf("media.example", "secret", "session=private", "192.168.1.55", "aa:bb:cc:dd:ee:ff")) {
            assertFalse(text.contains(privateText))
        }
    }

    @Test fun nestedCauseSurvivesAndMessagesAreBounded() {
        val root = UnsatisfiedLinkError("EVP_CIPHER_CTX_reset")
        val wrapped = RuntimeException("x".repeat(50_000), root)
        val text = DiagnosticReport.describe(wrapped)
        assertTrue(text.contains("Caused by: java.lang.UnsatisfiedLinkError"))
        assertTrue(text.contains("EVP_CIPHER_CTX_reset"))
        assertTrue(text.length <= 16_000)
    }
}
