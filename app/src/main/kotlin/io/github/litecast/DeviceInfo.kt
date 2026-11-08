/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast

import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * What the feedback page shows next to the survey's QR code, so people copy it instead of
 * hunting through the TV's settings. The model is the system's own name for the set (Sony's
 * "BRAVIA 4K VH2", Xiaomi's "MiTV-MFTR0"), which can differ from the box but is what tells
 * platforms apart.
 */
object DeviceInfo {
    data class Info(val brand: String, val model: String, val android: String, val app: String)

    fun current(context: Context) = Info(
        brand = brandLabel(Build.BRAND.orEmpty(), Build.MANUFACTURER.orEmpty(), Locale.getDefault().language == "zh"),
        model = Build.MODEL?.trim().orEmpty().ifEmpty { "—" },
        android = androidLabel(Build.VERSION.RELEASE.orEmpty(), Build.VERSION.SDK_INT),
        app = LiteCast.versionName(context),
    )

    // Chip and platform vendors that boxes report instead of the brand on the box
    private val generic = setOf("", "unknown", "android", "generic", "alps", "amlogic", "rockchip", "allwinner", "mstar", "realtek", "mediatek", "hisilicon")

    // Lower-case brand or manufacturer as Build reports it -> the name people pick in the survey
    private val chinese = linkedMapOf(
        "sony" to "索尼", "xiaomi" to "小米", "redmi" to "Redmi", "mitv" to "小米",
        "tcl" to "TCL", "ffalcon" to "雷鸟", "hisense" to "海信", "vidda" to "Vidda",
        "skyworth" to "创维", "coocaa" to "酷开", "changhong" to "长虹", "chiq" to "长虹",
        "konka" to "康佳", "kktv" to "康佳", "haier" to "海尔", "mooka" to "海尔",
        "sharp" to "夏普", "toshiba" to "东芝", "philips" to "飞利浦", "panasonic" to "松下",
        "letv" to "乐视", "leeco" to "乐视", "huawei" to "华为", "honor" to "荣耀",
        "samsung" to "三星", "lge" to "LG", "lg" to "LG",
        "xgimi" to "极米", "dangbei" to "当贝", "jmgo" to "坚果", "formovie" to "峰米", "fengmi" to "峰米",
    )

    /** The brand to show: Build.BRAND unless it is a chip vendor, then the manufacturer. */
    internal fun brandName(brand: String, manufacturer: String): String {
        val b = brand.trim()
        val m = manufacturer.trim()
        val pick = if (b.lowercase() !in generic) b else if (m.lowercase() !in generic) m else b.ifEmpty { m }
        if (pick.isEmpty()) return "—"
        // "xiaomi" -> "Xiaomi"; leave "TCL", "LGE" and mixed case alone
        return if (pick == pick.lowercase()) pick.replaceFirstChar { it.titlecase(Locale.ROOT) } else pick
    }

    internal fun brandLabel(brand: String, manufacturer: String, inChinese: Boolean): String {
        val name = brandName(brand, manufacturer)
        if (!inChinese) return name
        val local = chinese[name.lowercase()] ?: return name
        return if (local.equals(name, ignoreCase = true)) name else "$local（$name）"
    }

    internal fun androidLabel(release: String, sdk: Int): String =
        "Android " + release.trim().ifEmpty { "API $sdk" }
}
