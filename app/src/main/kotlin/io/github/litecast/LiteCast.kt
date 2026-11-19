/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import io.github.jqssun.airplay.Prefs
import io.github.litecast.tv.HomeActivity
import io.github.litecast.tv.PlayerActivity
import java.util.UUID

/** Glue between the upstream AirPlay receiver and LiteCast's TV front end. */
object LiteCast {
    const val PREF_DLNA_ENABLED = "litecast_dlna_enabled"
    /** False only after the user turns receiving off; boot start and sticky restarts honour it. */
    const val PREF_RECEIVER_ENABLED = "litecast_receiver_enabled"
    private const val PREF_DLNA_UUID = "litecast_dlna_uuid"
    private const val PREF_DEFAULTS_APPLIED = "litecast_defaults_v1"

    const val DEFAULT_NAME = "轻投"
    private const val PREF_SHORT_NAME_APPLIED = "litecast_short_name_v1"

    /** Apply first-install defaults and migrate only recognisable old generated names. */
    fun applyDefaults(context: Context) {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val defaultsApplied = prefs.getBoolean(PREF_DEFAULTS_APPLIED, false)
        val shortNameApplied = prefs.getBoolean(PREF_SHORT_NAME_APPLIED, false)
        if (defaultsApplied && shortNameApplied) return
        val saved = prefs.getString(Prefs.SERVER_NAME.key, null)
        val useDefault = saved.isNullOrBlank() || (!shortNameApplied && saved in legacyDefaultNames(context))
        prefs.edit {
            if (useDefault) putString(Prefs.SERVER_NAME.key, DEFAULT_NAME)
            if (!defaultsApplied) {
                putBoolean(Prefs.BOOT_AUTO_START.key, true)
                putBoolean(Prefs.AUTO_START.key, true)
                putBoolean(Prefs.RUN_IN_BACKGROUND.key, true)
                putBoolean(Prefs.LAUNCH_ON_CONNECT.key, true)
                putBoolean(PREF_DEFAULTS_APPLIED, true)
            }
            putBoolean(PREF_SHORT_NAME_APPLIED, true)
        }
    }

    private fun legacyDefaultNames(context: Context): Set<String> {
        val device = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }
            .getOrNull()?.trim().orEmpty()
        return setOf(device, Build.MODEL?.trim().orEmpty(), "Android TV")
            .filter { it.isNotEmpty() }
            .flatMap { name -> listOf("$name · 轻投".take(48), "$name · LiteCast".take(48)) }
            .toSet() + "Android AirPlay"
    }

    fun dlnaUuid(prefs: SharedPreferences): String {
        prefs.getString(PREF_DLNA_UUID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit { putString(PREF_DLNA_UUID, id) }
        return id
    }

    fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    fun homeIntent(context: Context): Intent = Intent(context, HomeActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun playerIntent(context: Context): Intent = Intent(context, PlayerActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
}
