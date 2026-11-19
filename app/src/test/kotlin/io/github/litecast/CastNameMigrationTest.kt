/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.jqssun.airplay.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CastNameMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    @Before fun reset() {
        prefs.edit().clear().commit()
        Settings.Global.putString(context.contentResolver, Settings.Global.DEVICE_NAME, "Sony BRAVIA")
    }

    @Test fun freshInstallUsesShortName() {
        LiteCast.applyDefaults(context)
        assertEquals("轻投", Prefs.SERVER_NAME.read(prefs))
    }

    @Test fun upgradesRecogniseOldDefaultsWithoutResettingOtherSettings() {
        val deviceNames = listOf("Sony BRAVIA", Build.MODEL, "Android TV", "X".repeat(60))
        for (device in deviceNames) {
            Settings.Global.putString(context.contentResolver, Settings.Global.DEVICE_NAME, device)
            for (suffix in listOf("轻投", "LiteCast")) {
                prefs.edit().clear()
                    .putBoolean("litecast_defaults_v1", true)
                    .putString(Prefs.SERVER_NAME.key, "$device · $suffix".take(48))
                    .putBoolean(Prefs.BOOT_AUTO_START.key, false)
                    .putBoolean(LiteCast.PREF_RECEIVER_ENABLED, false)
                    .putString("litecast_dlna_uuid", "existing-device-id")
                    .commit()
                LiteCast.applyDefaults(context)
                assertEquals("轻投", Prefs.SERVER_NAME.read(prefs))
                assertFalse(Prefs.BOOT_AUTO_START.read(prefs))
                assertFalse(prefs.getBoolean(LiteCast.PREF_RECEIVER_ENABLED, true))
                assertEquals("existing-device-id", LiteCast.dlnaUuid(prefs))
            }
        }
    }

    @Test fun customNamesSurviveUpgradeAndSubsequentStarts() {
        for (name in listOf("书房", "我的电视 · 轻投")) {
            prefs.edit().clear().putBoolean("litecast_defaults_v1", true)
                .putString(Prefs.SERVER_NAME.key, name).commit()
            LiteCast.applyDefaults(context)
            LiteCast.applyDefaults(context)
            assertEquals(name, Prefs.SERVER_NAME.read(prefs))
        }
        // Even a deliberate later choice matching an old default is not migrated again.
        prefs.edit().putString(Prefs.SERVER_NAME.key, "Sony BRAVIA · 轻投").commit()
        LiteCast.applyDefaults(context)
        assertEquals("Sony BRAVIA · 轻投", Prefs.SERVER_NAME.read(prefs))
    }
}
