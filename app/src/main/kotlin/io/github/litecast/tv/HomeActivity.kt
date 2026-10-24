/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.core.content.edit
import io.github.jqssun.airplay.MainActivity
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.get
import io.github.jqssun.airplay.hasLocalNetwork
import io.github.jqssun.airplay.observe
import io.github.jqssun.airplay.service.AirPlayService
import io.github.litecast.LiteCast
import io.github.litecast.dlna.DlnaRenderer
import java.net.Inet4Address
import kotlinx.coroutines.delay

/** Launcher entry: one calm screen that says what to do on the phone, plus a short settings list. */
class HomeActivity : ComponentActivity() {
    private val binding = ServiceBinding(this)
    private val screen = mutableStateOf(Screen.HOME)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        binding.service.value?.let(::ensureRunning)
    }

    private enum class Screen { HOME, SETTINGS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.onConnected = { svc -> ensureRunning(svc) }
        // Android 17+ gates LAN access behind a runtime permission
        if (!hasLocalNetwork()) permissions.launch(arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK))
        setContent {
            LiteCastTheme {
                val svc = binding.service.value
                when (screen.value) {
                    Screen.HOME -> HomeScreen(svc, onSettings = { screen.value = Screen.SETTINGS })
                    Screen.SETTINGS -> {
                        BackHandler { screen.value = Screen.HOME }
                        SettingsScreen(svc)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.bind()
        binding.service.value?.let(::ensureRunning)
    }

    override fun onStop() {
        binding.unbind()
        super.onStop()
    }

    private fun ensureRunning(svc: AirPlayService) {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        if (prefs.getBoolean(LiteCast.PREF_RECEIVER_ENABLED, true) && svc.serverState.value != AirPlayService.ServerState.RUNNING) {
            ServiceBinding.startReceiver(this)
        }
    }
}

// ---------------------------------------------------------------------------------------- home

internal data class HomeUi(
    val name: String,
    val airplay: AirPlayService.ServerState,
    val dlna: DlnaRenderer.Status,
    val dlnaEnabled: Boolean,
    val ip: String,
    val canPopUp: Boolean,
    val sessionActive: Boolean,
    val receiverPaused: Boolean,
    val packageName: String,
)

@Composable
private fun HomeScreen(svc: AirPlayService?, onSettings: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE) }
    val name by remember { prefs.observe { this[Prefs.SERVER_NAME] } }.collectAsState(prefs[Prefs.SERVER_NAME])
    val serverState = svc?.serverState.collectOr(AirPlayService.ServerState.STOPPED)
    val dlnaStatus = svc?.dlnaStatus.collectOr(DlnaRenderer.Status.STOPPED)
    val dlnaEnabled by remember { prefs.observe { getBoolean(LiteCast.PREF_DLNA_ENABLED, true) } }.collectAsState(true)
    val mirroring = svc?.mirroringActive.collectOr(false)
    val video = svc?.videoPlaybackActive.collectOr(false)
    val music = svc?.audioOnly.collectOr(false)
    val ip by produceState("", svc) {
        while (true) {
            value = svc?.dlnaAddress()?.ifEmpty { null } ?: lanIp(context)
            delay(2000)
        }
    }
    val canPopUp by produceState(canDrawOverlays(context)) {
        while (true) { value = canDrawOverlays(context); delay(1000) }
    }
    var renaming by remember { mutableStateOf(false) }

    HomeContent(
        ui = HomeUi(
            name = name,
            airplay = serverState,
            dlna = dlnaStatus,
            dlnaEnabled = dlnaEnabled,
            ip = ip,
            canPopUp = canPopUp,
            sessionActive = mirroring || video || music,
            receiverPaused = svc != null && serverState == AirPlayService.ServerState.STOPPED,
            packageName = context.packageName,
        ),
        onRename = { renaming = true },
        onSettings = onSettings,
        onBackToCast = { context.startActivity(LiteCast.playerIntent(context)) },
        onStartReceiver = { svc?.startServer() },
        onAllowPopUp = { openOverlaySettings(context) },
    )

    if (renaming) {
        RenameDialog(
            initial = name,
            onDismiss = { renaming = false },
            onSave = { value ->
                prefs.edit { putString(Prefs.SERVER_NAME.key, value) }
                renaming = false
            },
        )
    }
}

@Composable
internal fun HomeContent(
    ui: HomeUi,
    onRename: () -> Unit,
    onSettings: () -> Unit,
    onBackToCast: () -> Unit,
    onStartReceiver: () -> Unit,
    onAllowPopUp: () -> Unit,
) {
    val noticeFocus = remember { FocusRequester() }
    val castFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val notice = when {
        ui.receiverPaused -> Notice.PAUSED
        !ui.canPopUp -> Notice.POPUP
        else -> null
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Tv.BackgroundTop, Tv.Background)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Tv.AccentSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Cast, contentDescription = null, tint = Tv.Accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.lc_app_name), style = Tv.Label.copy(fontSize = Tv.Body.fontSize))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.lc_tagline), style = Tv.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (ui.sessionActive) {
                    PillButton(stringResource(R.string.lc_back_to_cast), Icons.Rounded.PlayCircle, Modifier.focusRequester(castFocus), emphasized = true, onClick = onBackToCast)
                    Spacer(Modifier.width(12.dp))
                }
                PillButton(stringResource(R.string.lc_action_rename), Icons.Rounded.Edit, onClick = onRename)
                Spacer(Modifier.width(12.dp))
                PillButton(stringResource(R.string.lc_action_settings), Icons.Rounded.Settings, Modifier.focusRequester(settingsFocus), onClick = onSettings)
            }

            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                // left: who am I, am I ready, anything to fix
                Column(Modifier.weight(1.1f).fillMaxHeight()) {
                    Text(stringResource(R.string.lc_device_label), style = Tv.Caption)
                    Spacer(Modifier.height(6.dp))
                    Text(ui.name, style = Tv.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (ui.airplay) {
                            AirPlayService.ServerState.RUNNING -> StatusChip(stringResource(R.string.lc_status_airplay_ready), Tv.Accent)
                            AirPlayService.ServerState.ERROR -> StatusChip(stringResource(R.string.lc_status_airplay_error), Tv.Error)
                            AirPlayService.ServerState.STOPPED -> StatusChip(stringResource(R.string.lc_status_airplay_off), Tv.TextTertiary)
                        }
                        when {
                            !ui.dlnaEnabled || ui.airplay != AirPlayService.ServerState.RUNNING ->
                                StatusChip(stringResource(R.string.lc_status_dlna_off), Tv.TextTertiary)
                            ui.dlna == DlnaRenderer.Status.READY -> StatusChip(stringResource(R.string.lc_status_dlna_ready), Tv.Accent)
                            ui.dlna == DlnaRenderer.Status.ERROR -> StatusChip(stringResource(R.string.lc_status_dlna_error), Tv.Error)
                            else -> StatusChip(stringResource(R.string.lc_status_dlna_starting), Tv.TextTertiary)
                        }
                    }
                    if (ui.ip.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.lc_status_ip, ui.ip), style = Tv.Caption)
                    }
                    Spacer(Modifier.weight(1f))
                    when (notice) {
                        Notice.PAUSED -> NoticeBar(
                            title = stringResource(R.string.lc_receiver_paused),
                            body = stringResource(R.string.lc_receiver_paused_body),
                            action = stringResource(R.string.lc_receiver_start),
                            actionModifier = Modifier.focusRequester(noticeFocus),
                            onAction = onStartReceiver,
                        )
                        Notice.POPUP -> NoticeBar(
                            title = stringResource(R.string.lc_overlay_title),
                            body = stringResource(R.string.lc_overlay_body),
                            action = stringResource(R.string.lc_overlay_button),
                            footnote = stringResource(R.string.lc_overlay_adb, ui.packageName),
                            actionModifier = Modifier.focusRequester(noticeFocus),
                            onAction = onAllowPopUp,
                        )
                        null -> Unit
                    }
                }
                Spacer(Modifier.width(36.dp))
                // right: how to cast, one card per kind of sender
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoCard(Icons.Rounded.PhoneIphone, stringResource(R.string.lc_card_apple_title), stringResource(R.string.lc_card_apple_body), Modifier.fillMaxWidth())
                    InfoCard(Icons.Rounded.OndemandVideo, stringResource(R.string.lc_card_apps_title), stringResource(R.string.lc_card_apps_body), Modifier.fillMaxWidth())
                }
            }
        }
    }

    // Focus the one thing worth doing: fix a problem, return to a cast, or settings.
    LaunchedEffect(notice, ui.sessionActive) {
        runCatching {
            when {
                notice != null -> noticeFocus.requestFocus()
                ui.sessionActive -> castFocus.requestFocus()
                else -> settingsFocus.requestFocus()
            }
        }
    }
}

private enum class Notice { PAUSED, POPUP }

@Composable
private fun NoticeBar(
    title: String,
    body: String,
    action: String,
    footnote: String? = null,
    actionModifier: Modifier = Modifier,
    onAction: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Tv.WarningSoft)
            .border(1.dp, Tv.Warning.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Tv.Warning, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = Tv.Label.copy(fontSize = Tv.Body.fontSize))
        }
        Spacer(Modifier.height(6.dp))
        Text(body, style = Tv.Caption.copy(color = Tv.TextSecondary, lineHeight = Tv.Caption.fontSize * 1.45f))
        if (footnote != null) {
            Spacer(Modifier.height(6.dp))
            Text(footnote, style = Tv.Caption.copy(fontSize = Tv.Caption.fontSize * 0.8f))
        }
        Spacer(Modifier.height(12.dp))
        PillButton(action, modifier = actionModifier, emphasized = true, onClick = onAction)
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tv.SurfaceRaised,
        title = { Text(stringResource(R.string.lc_rename_title), style = Tv.Title) },
        text = {
            Column {
                Text(stringResource(R.string.lc_rename_body), style = Tv.Caption)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(48) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { text.trim().takeIf { it.isNotEmpty() }?.let(onSave) }) { Text(stringResource(R.string.lc_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lc_cancel)) } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

// ---------------------------------------------------------------------------------------- settings

internal data class SettingsUi(
    val receiverOn: Boolean,
    val dlna: Boolean,
    val boot: Boolean,
    val canPopUp: Boolean,
    val pin: Boolean,
    val version: String,
)

@Composable
private fun SettingsScreen(svc: AirPlayService?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE) }
    val serverState = svc?.serverState.collectOr(AirPlayService.ServerState.STOPPED)
    val boot by remember { prefs.observe { this[Prefs.BOOT_AUTO_START] } }.collectAsState(prefs[Prefs.BOOT_AUTO_START])
    val dlna by remember { prefs.observe { getBoolean(LiteCast.PREF_DLNA_ENABLED, true) } }.collectAsState(true)
    val pin by remember { prefs.observe { this[Prefs.REQUIRE_PIN] } }.collectAsState(prefs[Prefs.REQUIRE_PIN])
    val canPopUp by produceState(canDrawOverlays(context)) {
        while (true) { value = canDrawOverlays(context); delay(1000) }
    }
    var about by remember { mutableStateOf(false) }
    val running = serverState == AirPlayService.ServerState.RUNNING

    SettingsContent(
        ui = SettingsUi(running, dlna, boot, canPopUp, pin, LiteCast.versionName(context)),
        onToggleReceiver = { if (running) svc?.stopServer() else svc?.startServer() },
        onToggleDlna = { svc?.setDlnaEnabled(!dlna) ?: prefs.edit { putBoolean(LiteCast.PREF_DLNA_ENABLED, !dlna) } },
        onToggleBoot = { prefs.edit { putBoolean(Prefs.BOOT_AUTO_START.key, !boot) } },
        onPopUp = { openOverlaySettings(context) },
        onTogglePin = { prefs.edit { putBoolean(Prefs.REQUIRE_PIN.key, !pin) } },
        onAdvanced = { context.startActivity(Intent(context, MainActivity::class.java)) },
        onAbout = { about = true },
    )

    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            containerColor = Tv.SurfaceRaised,
            title = { Text(stringResource(R.string.lc_app_name), style = Tv.Title) },
            text = { Text(stringResource(R.string.lc_about_body, LiteCast.versionName(context)), style = Tv.Body) },
            confirmButton = { TextButton(onClick = { about = false }) { Text(stringResource(R.string.lc_ok)) } },
        )
    }
}

@Composable
internal fun SettingsContent(
    ui: SettingsUi,
    onToggleReceiver: () -> Unit,
    onToggleDlna: () -> Unit,
    onToggleBoot: () -> Unit,
    onPopUp: () -> Unit,
    onTogglePin: () -> Unit,
    onAdvanced: () -> Unit,
    onAbout: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val on = stringResource(R.string.lc_on)
    val off = stringResource(R.string.lc_off)
    fun flag(value: Boolean) = if (value) on else off

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Tv.BackgroundTop, Tv.Background)))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp, vertical = 40.dp)
        ) {
            Text(stringResource(R.string.lc_settings_title), style = Tv.Title)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingRow(stringResource(R.string.lc_setting_receiver), stringResource(R.string.lc_setting_receiver_desc),
                    flag(ui.receiverOn), ui.receiverOn, Modifier.focusRequester(first), onToggleReceiver)
                SettingRow(stringResource(R.string.lc_setting_dlna), stringResource(R.string.lc_setting_dlna_desc), flag(ui.dlna), ui.dlna, onClick = onToggleDlna)
                SettingRow(stringResource(R.string.lc_setting_boot), stringResource(R.string.lc_setting_boot_desc), flag(ui.boot), ui.boot, onClick = onToggleBoot)
                SettingRow(stringResource(R.string.lc_setting_popup),
                    stringResource(if (ui.canPopUp) R.string.lc_setting_popup_on else R.string.lc_setting_popup_off),
                    flag(ui.canPopUp), ui.canPopUp, onClick = onPopUp)
                SettingRow(stringResource(R.string.lc_setting_pin), stringResource(R.string.lc_setting_pin_desc), flag(ui.pin), ui.pin, onClick = onTogglePin)
                SettingRow(stringResource(R.string.lc_setting_advanced), stringResource(R.string.lc_setting_advanced_desc), null, onClick = onAdvanced)
                SettingRow(stringResource(R.string.lc_setting_about), ui.version, null, onClick = onAbout)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

// ---------------------------------------------------------------------------------------- helpers

internal fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

internal fun openOverlaySettings(context: Context) {
    val pkg = Uri.parse("package:${context.packageName}")
    val candidates = listOf(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

private fun lanIp(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = cm.activeNetwork ?: return ""
    return cm.getLinkProperties(network)?.linkAddresses
        ?.map { it.address }
        ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
        ?.hostAddress.orEmpty()
}
