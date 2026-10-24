package io.github.jqssun.airplay

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.litecast.LiteCast

@HiltAndroidApp
class AirPlayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // LiteCast: TV-friendly defaults (device name, start on boot) on first run
        LiteCast.applyDefaults(this)
    }
}
