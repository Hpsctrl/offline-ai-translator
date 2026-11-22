package com.example.offlineaitranslator.ui.coui

import android.os.Build
import android.view.Display
import android.view.Window

/**
 * Requests the display's highest available refresh rate (120 Hz / 144 Hz where supported).
 *
 * Compose animations in this app are time-based (`tween`, `spring`, `Animatable`), so they already
 * interpolate correctly at any frame rate — the missing piece is asking Android to actually run
 * the panel above 60 Hz while the app is in the foreground.
 *
 * The chosen mode keeps the current resolution (or the closest higher one), so we never drop to a
 * lower-resolution mode just to gain hertz. Falls back to `preferredRefreshRate` on older APIs.
 */
object CouiRefreshRate {

    fun apply(window: Window) {
        @Suppress("DEPRECATION")
        val display: Display = window.windowManager.defaultDisplay ?: return
        val modes = display.supportedModes
        if (modes.isEmpty()) return

        val current = display.mode
        val candidates = modes.filter {
            it.physicalWidth >= current.physicalWidth && it.physicalHeight >= current.physicalHeight
        }
        val best = (candidates.ifEmpty { modes.toList() }).maxByOrNull { it.refreshRate } ?: return

        val params = window.attributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            params.preferredDisplayModeId = best.modeId
        }
        params.preferredRefreshRate = best.refreshRate
        window.attributes = params
    }
}
