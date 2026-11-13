/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast

/**
 * Which TVs need a stronger idle keep-alive so phones can still find the receiver when
 * the LiteCast UI is not in front. Xiaomi / PatchWall sets and Android 6 (and older) TVs
 * are known to freeze or kill an idle advertised service. Phones and modern non-Xiaomi
 * TVs keep the existing "wake only while something is being cast" behaviour.
 *
 * Some older Xiaomi TVs do not report leanback / television UI mode, so a `MiTV*` model
 * is treated as a TV even when [isTv] is false. Xiaomi phones do not use that model name.
 */
object ReceiverKeepAlive {

    /** Xiaomi / Redmi / MiTV as reported in Build.BRAND, MANUFACTURER or MODEL. */
    internal fun isXiaomiFamily(brand: String, manufacturer: String, model: String): Boolean =
        looksLikeXiaomi(brand) || looksLikeXiaomi(manufacturer) || looksLikeXiaomi(model)

    /**
     * @param isTv whether this is a television / leanback device, not a phone
     * @param sdk [android.os.Build.VERSION.SDK_INT]
     */
    fun needsIdleDiscoveryKeepAlive(
        isTv: Boolean,
        brand: String,
        manufacturer: String,
        model: String,
        sdk: Int,
    ): Boolean {
        if (isXiaomiTelevisionModel(model)) return true
        if (!isTv) return false
        return isXiaomiFamily(brand, manufacturer, model) || sdk <= 23
    }

    /** `MiTV3S-55`, `MiTV-MFTR0` and similar — a television, even without leanback. */
    internal fun isXiaomiTelevisionModel(model: String): Boolean {
        val tokens = model.trim().lowercase().split(' ', '-', '_', '/', '.')
        return tokens.any { it == "mitv" || it.startsWith("mitv") }
    }

    private fun looksLikeXiaomi(raw: String): Boolean {
        val value = raw.trim().lowercase()
        if (value.isEmpty()) return false
        if (value == "xiaomi" || value == "redmi" || value == "mitv") return true
        if (value.startsWith("xiaomi") || value.startsWith("redmi") || value.startsWith("mitv")) return true
        if (value.contains("xiaomi") || value.contains("redmi")) return true
        return value.split(' ', '-', '_', '/', '.').any { it == "mitv" || it.startsWith("mitv") }
    }
}
