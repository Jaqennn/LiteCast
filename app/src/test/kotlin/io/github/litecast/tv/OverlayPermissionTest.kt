/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class OverlayPermissionTest {

    @Test
    fun android8And9DoNotRequireOverlayPermissionForAutoPopup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(canDrawOverlays(context, sdkInt = Build.VERSION_CODES.O, overlayGranted = { false }))
        assertTrue(canDrawOverlays(context, sdkInt = Build.VERSION_CODES.P, overlayGranted = { false }))
    }

    @Test
    fun android10PlusChecksOverlayPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(canDrawOverlays(context, sdkInt = Build.VERSION_CODES.Q, overlayGranted = { false }))
        assertTrue(canDrawOverlays(context, sdkInt = Build.VERSION_CODES.Q, overlayGranted = { true }))
    }

    @Test
    fun openOverlaySettingsShowsFallbackToastWhenNoActivityResolves() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Default Robolectric PackageManager has no system Settings activities registered.
        val opened = openOverlaySettings(context)
        assertFalse(opened)
        val toast = ShadowToast.getTextOfLatestToast()
        assertNotNull(toast)
        assertTrue(toast!!.contains("SYSTEM_ALERT_WINDOW"))
    }
}
