/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import io.github.jqssun.airplay.service.AirPlayService

/** Binds an activity to the receiver service and exposes it as Compose state. */
class ServiceBinding(private val activity: Activity) {
    val service = mutableStateOf<AirPlayService?>(null)
    var onConnected: ((AirPlayService) -> Unit)? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? AirPlayService.LocalBinder)?.service ?: return
            service.value = svc
            onConnected?.invoke(svc)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service.value = null
        }
    }

    fun bind() {
        if (bound) return
        bound = activity.bindService(Intent(activity, AirPlayService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (!bound) return
        runCatching { activity.unbindService(connection) }
        bound = false
        service.value = null
    }

    companion object {
        /** Starts the foreground receiver (same path the boot receiver uses). */
        fun startReceiver(context: Context) {
            val intent = Intent(context, AirPlayService::class.java).setAction(AirPlayService.ACTION_START_SERVER)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
