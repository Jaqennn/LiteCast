package io.github.jqssun.airplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.jqssun.airplay.service.AirPlayService
import io.github.litecast.LiteCast

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // LiteCast (after sony-airplay-tv): also restart after an app update, and never crash the receiver
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs[Prefs.BOOT_AUTO_START]) return
        if (!prefs.getBoolean(LiteCast.PREF_RECEIVER_ENABLED, true)) return

        val serviceIntent = Intent(context, AirPlayService::class.java)
            .setAction(AirPlayService.ACTION_START_SERVER)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: IllegalStateException) {
            Log.w("BootReceiver", "background start blocked; open the app once", e)
        } catch (e: SecurityException) {
            Log.w("BootReceiver", "background start not permitted", e)
        }
    }
}
