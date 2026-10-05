package com.kairo.app.service.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.kairo.app.domain.focus.ClockReading

/** Reads both clocks plus the boot counter, so stored sessions can tell "same boot" from "after a reboot". */
class AndroidFocusClock(private val context: Context) {
    fun read(): ClockReading = ClockReading(
        wallMillis = System.currentTimeMillis(),
        elapsedMillis = SystemClock.elapsedRealtime(),
        // BOOT_COUNT (API 24+) only ever increases; -1 if a ROM hides it, which makes us use wall-clock time.
        bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
    )
}
