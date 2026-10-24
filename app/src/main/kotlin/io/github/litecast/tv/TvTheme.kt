/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One calm palette, readable from the sofa: near-black backgrounds, a single mint accent
 * for "ready / focused / playing", amber only when the user has something to do.
 */
object Tv {
    val Background = Color(0xFF0B0D10)
    val BackgroundTop = Color(0xFF12171D)
    val Surface = Color(0xFF161A20)
    val SurfaceRaised = Color(0xFF1D222A)
    val Stroke = Color(0xFF2A303A)
    val TextPrimary = Color(0xFFF2F4F7)
    val TextSecondary = Color(0xFFA9B0BC)
    val TextTertiary = Color(0xFF6E7683)
    val Accent = Color(0xFF5EE2B7)
    val AccentSoft = Color(0x245EE2B7)
    val Warning = Color(0xFFF6C35B)
    val WarningSoft = Color(0x1FF6C35B)
    val Error = Color(0xFFFF6B6B)
    val Scrim = Color(0xB3000000)

    val Display = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, letterSpacing = 0.sp)
    val Title = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    val Body = TextStyle(fontSize = 18.sp, color = TextSecondary, lineHeight = 26.sp)
    val Label = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    val Caption = TextStyle(fontSize = 15.sp, color = TextTertiary)
}

@Composable
fun LiteCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tv.Accent,
            onPrimary = Color(0xFF00281C),
            secondary = Tv.Accent,
            background = Tv.Background,
            onBackground = Tv.TextPrimary,
            surface = Tv.Surface,
            onSurface = Tv.TextPrimary,
            surfaceVariant = Tv.SurfaceRaised,
            onSurfaceVariant = Tv.TextSecondary,
            outline = Tv.Stroke,
            error = Tv.Error,
        ),
        content = content,
    )
}
