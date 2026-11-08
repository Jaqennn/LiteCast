package io.github.jqssun.airplay

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

// physical panel resolution
fun Context.realDisplaySize(): Pair<Int, Int> {
    val display = (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
    if (Build.VERSION.SDK_INT >= 23) {
        val mode = display?.mode
        if (mode != null) return mode.physicalWidth to mode.physicalHeight
    } else if (display != null) {
        // LiteCast: Display.Mode needs Android 6.0; on 5.x the real size is the best we can get
        val size = Point()
        @Suppress("DEPRECATION") display.getRealSize(size)
        if (size.x > 0 && size.y > 0) return size.x to size.y
    }
    return resources.displayMetrics.let { it.widthPixels to it.heightPixels }
}
