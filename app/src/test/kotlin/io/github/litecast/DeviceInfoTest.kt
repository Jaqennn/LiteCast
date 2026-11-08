/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceInfoTest {

    @Test
    fun brandComesFromBuildBrandUnlessItIsAChipVendor() {
        assertEquals("Sony", DeviceInfo.brandName("Sony", "Sony"))
        assertEquals("Xiaomi", DeviceInfo.brandName("xiaomi", "Xiaomi"))
        assertEquals("TCL", DeviceInfo.brandName("TCL", "TCL"))
        // boxes that report the chip or plain "Android" as their brand
        assertEquals("Dangbei", DeviceInfo.brandName("Android", "dangbei"))
        assertEquals("Amlogic", DeviceInfo.brandName("amlogic", "amlogic"))
        assertEquals("—", DeviceInfo.brandName("", ""))
    }

    @Test
    fun chineseLabelsUseTheNamesInTheSurvey() {
        assertEquals("索尼（Sony）", DeviceInfo.brandLabel("Sony", "Sony", inChinese = true))
        assertEquals("小米（Xiaomi）", DeviceInfo.brandLabel("Xiaomi", "Xiaomi", inChinese = true))
        assertEquals("海信（Hisense）", DeviceInfo.brandLabel("Hisense", "Hisense", inChinese = true))
        assertEquals("当贝（Dangbei）", DeviceInfo.brandLabel("Android", "dangbei", inChinese = true))
        assertEquals("TCL", DeviceInfo.brandLabel("TCL", "TCL", inChinese = true))
        assertEquals("Foo", DeviceInfo.brandLabel("foo", "foo", inChinese = true))
        assertEquals("Sony", DeviceInfo.brandLabel("Sony", "Sony", inChinese = false))
    }

    @Test
    fun androidVersionReadsLikeTheSurveyChoices() {
        assertEquals("Android 10", DeviceInfo.androidLabel("10", 29))
        assertEquals("Android 4.4.2", DeviceInfo.androidLabel("4.4.2", 19))
        assertEquals("Android API 21", DeviceInfo.androidLabel(" ", 21))
    }
}
