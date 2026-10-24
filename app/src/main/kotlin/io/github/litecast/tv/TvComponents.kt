/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Focusable for both remote and touch. `clickable` already answers OK/Enter on a focused node;
 * the focused state is the only decoration (no ripple on a TV).
 */
@Composable
fun Modifier.tvClickable(onClick: () -> Unit, onFocus: (Boolean) -> Unit = {}): Modifier =
    this
        .onFocusChanged { onFocus(it.isFocused) }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@Composable
fun PillButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            focused -> Tv.Accent
            emphasized -> Tv.AccentSoft
            else -> Tv.SurfaceRaised
        }, label = "pillBg"
    )
    val fg = if (focused) Color(0xFF04140E) else if (emphasized) Tv.Accent else Tv.TextPrimary
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "pillScale")
    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .tvClickable(onClick) { focused = it }
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Tv.Label.copy(color = fg))
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
fun StatusChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color)
        Spacer(Modifier.width(8.dp))
        Text(label, style = Tv.Body.copy(color = Tv.TextSecondary))
    }
}

@Composable
fun InfoCard(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Tv.Surface)
            .border(1.dp, Tv.Stroke, RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Tv.AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Tv.Accent, modifier = Modifier.size(22.dp)) }
        Text(title, style = Tv.Label.copy(fontSize = Tv.Title.fontSize * 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(body, style = Tv.Body)
    }
}

/** Collects a flow from a service that may not be bound yet, with the same call shape either way. */
@Composable
fun <T> StateFlow<T>?.collectOr(default: T): T {
    val flow = remember(this) { this ?: MutableStateFlow(default) }
    return flow.collectAsState().value
}

/** A settings row: title + description on the left, value on the right; OK toggles / opens. */
@Composable
fun SettingRow(
    title: String,
    description: String?,
    value: String?,
    valueOn: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Tv.SurfaceRaised else Color.Transparent, label = "rowBg")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, if (focused) Tv.Accent else Color.Transparent, RoundedCornerShape(16.dp))
            .tvClickable(onClick) { focused = it }
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Tv.Label.copy(fontSize = Tv.Body.fontSize))
            if (!description.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(description, style = Tv.Caption)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(16.dp))
            Text(value, style = Tv.Label.copy(color = if (valueOn) Tv.Accent else Tv.TextTertiary))
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, buffered: Float = 0f) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
        if (buffered > 0f) {
            Box(Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).height(6.dp).background(Color(0x40FFFFFF)))
        }
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Tv.Accent))
    }
}

@Composable
fun SectionPadding(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 56.dp, vertical = 40.dp)) { content() }
}
