/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityLabelTest {
    @Test
    fun labelsFollowWhatPeopleCallTheseSizes() {
        assertEquals("4K", qualityLabel(3840, 2160))
        assertEquals("4K", qualityLabel(2160, 3840))   // vertical 4K
        assertEquals("4K", qualityLabel(3840, 1600))   // scope film
        assertEquals("2K", qualityLabel(2560, 1440))
        assertEquals("1080P", qualityLabel(1920, 1080))
        assertEquals("1080P", qualityLabel(1080, 1920)) // vertical phone video
        assertEquals("1080P", qualityLabel(1920, 800))
        assertEquals("1080P", qualityLabel(1080, 1080))
        assertEquals("720P", qualityLabel(1280, 720))
        assertEquals("720P", qualityLabel(720, 1280))
        assertEquals("540P", qualityLabel(960, 540))
        assertNull(qualityLabel(0, 0))
    }
}
