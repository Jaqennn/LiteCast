/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import android.content.Intent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.litecast.DeviceInfo

/**
 * The survey's QR code with this TV's brand, model and versions beside it: people scan with the
 * phone (no account, opens in mainland China) and copy the details instead of looking them up.
 * Nothing leaves the TV; the QR image is bundled and only the phone talks to the survey.
 */
@Composable
internal fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val info = remember { DeviceInfo.current(context) }
    FeedbackContent(info, onBack)
}

@Composable
internal fun FeedbackContent(info: DeviceInfo.Info, onBack: () -> Unit) {
    val context = LocalContext.current
    val back = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Tv.BackgroundTop, Tv.Background)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lc_feedback_title), style = Tv.Title, modifier = Modifier.weight(1f))
                PillButton(stringResource(R.string.lc_diag_title), onClick = {
                    context.startActivity(Intent(context, DiagnosticActivity::class.java))
                })
                Spacer(Modifier.width(12.dp))
                PillButton(stringResource(R.string.lc_feedback_back), Icons.AutoMirrored.Rounded.ArrowBack, Modifier.focusRequester(back), onClick = onBack)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.lc_feedback_body), style = Tv.Body)
            Spacer(Modifier.height(28.dp))
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(232.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(6.dp)) {
                        Image(
                            bitmap = ImageBitmap.imageResource(R.drawable.lc_feedback_qr),
                            contentDescription = stringResource(R.string.lc_feedback_qr),
                            modifier = Modifier.fillMaxSize(),
                            filterQuality = FilterQuality.None,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.lc_feedback_scan), style = Tv.Caption)
                }
                Spacer(Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lc_feedback_copy), style = Tv.Label.copy(fontSize = Tv.Body.fontSize))
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier
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
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.lc_feedback_model_note), style = Tv.Caption)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { back.requestFocus() } }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Tv.Body, modifier = Modifier.width(118.dp))
        Text(value, style = Tv.Title.copy(fontSize = Tv.Title.fontSize * 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
