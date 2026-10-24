/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.jqssun.airplay.Prefs
import io.github.litecast.LiteCast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Starts the real activities (manifest, themes, Compose) on Android 10, the X90J's version. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-w960dp-h540dp-land-television-xhdpi")
class ActivitySmokeTest {

    @Test
    fun homeScreenStartsAndAsksTheReceiverToRun() {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun playerWaitsForTheServiceInsteadOfClosing() {
        val controller = Robolectric.buildActivity(PlayerActivity::class.java).setup()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun firstRunDefaultsMakeItAnAlwaysOnReceiver() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean(Prefs.BOOT_AUTO_START.key, false))
        assertTrue(prefs.getBoolean(LiteCast.PREF_RECEIVER_ENABLED, true))
        assertTrue(prefs.getString(Prefs.SERVER_NAME.key, "")!!.endsWith("· 轻投"))
        assertEquals(context.getString(io.github.jqssun.airplay.R.string.lc_app_name), "轻投")
    }
}
