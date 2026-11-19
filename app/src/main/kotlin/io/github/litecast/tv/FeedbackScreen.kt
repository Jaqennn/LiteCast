/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.litecast.DeviceInfo

@Composable
internal fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val info = remember { DeviceInfo.current(context) }
    FeedbackContent(info, onBack)
}

@Composable
internal fun FeedbackContent(info: DeviceInfo.Info, onBack: () -> Unit) {
    val context = LocalContext.current
    val diagnostics = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Tv.BackgroundTop, Tv.Background)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lc_feedback_title), style = Tv.Title, modifier = Modifier.weight(1f))
                PillButton(
                    stringResource(R.string.lc_diag_title),
                    modifier = Modifier.focusRequester(diagnostics),
                    emphasized = true,
                    onClick = { context.startActivity(Intent(context, DiagnosticActivity::class.java)) },
                )
                Spacer(Modifier.width(12.dp))
                PillButton(stringResource(R.string.lc_feedback_back), Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.lc_feedback_body), style = Tv.Body)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.lc_feedback_channels), style = Tv.Body)
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Tv.Surface)
                    .border(1.dp, Tv.Stroke, RoundedCornerShape(20.dp))
                    .padding(horizontal = 26.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoLine(stringResource(R.string.lc_feedback_brand), info.brand)
                InfoLine(stringResource(R.string.lc_feedback_model), info.model)
                InfoLine(stringResource(R.string.lc_feedback_android), info.android)
                InfoLine(stringResource(R.string.lc_feedback_version), info.app)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { diagnostics.requestFocus() } }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Tv.Body, modifier = Modifier.width(118.dp))
        Text(value, style = Tv.Title.copy(fontSize = Tv.Title.fontSize * 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
