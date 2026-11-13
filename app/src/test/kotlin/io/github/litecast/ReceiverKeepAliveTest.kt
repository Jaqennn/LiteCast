/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverKeepAliveTest {

    @Test
    fun xiaomiTv3sNeedsIdleKeepAlive() {
        assertTrue(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = true,
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                model = "MiTV3S-55",
                sdk = 23,
            ),
        )
        assertTrue(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = true,
                brand = "xiaomi",
                manufacturer = "Xiaomi",
                model = "MiTV3S",
                sdk = 23,
            ),
        )
    }

    @Test
    fun mitvModelIsATvEvenWithoutLeanback() {
        assertTrue(ReceiverKeepAlive.isXiaomiTelevisionModel("MiTV3S-55"))
        assertTrue(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = false,
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                model = "MiTV3S-55",
                sdk = 23,
            ),
        )
        assertFalse(ReceiverKeepAlive.isXiaomiTelevisionModel("23127PN0CC"))
        assertFalse(ReceiverKeepAlive.isXiaomiTelevisionModel("Redmi Note 12"))
    }

    @Test
    fun mitvModelIsXiaomiEvenIfBrandIsAChipVendor() {
        assertTrue(ReceiverKeepAlive.isXiaomiFamily("Android", "amlogic", "MiTV-MFTR0"))
        assertTrue(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = true,
                brand = "Android",
                manufacturer = "amlogic",
                model = "MiTV-MFTR0",
                sdk = 34,
            ),
        )
    }

    @Test
    fun redmiTvIsXiaomiFamily() {
        assertTrue(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = true,
                brand = "Redmi",
                manufacturer = "Xiaomi",
                model = "Redmi TV X65",
                sdk = 28,
            ),
        )
    }

    @Test
    fun android6NonXiaomiTvNeedsIdleKeepAlive() {
        assertTrue(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = true,
                brand = "TCL",
                manufacturer = "TCL",
                model = "Smart TV",
                sdk = 23,
            ),
        )
        assertTrue(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = true,
                brand = "Hisense",
                manufacturer = "Hisense",
                model = "VIDAA",
                sdk = 22,
            ),
        )
    }

    @Test
    fun phoneNeverGetsIdleKeepAlive() {
        assertFalse(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = false,
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                model = "23127PN0CC",
                sdk = 34,
            ),
        )
        assertFalse(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = false,
                brand = "Sony",
                manufacturer = "Sony",
                model = "XQ-DQ72",
                sdk = 23,
            ),
        )
    }

    @Test
    fun modernNonXiaomiTvKeepsExistingBehaviour() {
        assertFalse(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = true,
                brand = "Sony",
                manufacturer = "Sony",
                model = "BRAVIA 4K VH2",
                sdk = 31,
            ),
        )
        assertFalse(
            ReceiverKeepAlive.needsIdleDiscoveryKeepAlive(
                isTv = true,
                brand = "TCL",
                manufacturer = "TCL",
                model = "Smart TV",
                sdk = 24,
            ),
        )
    }
}
