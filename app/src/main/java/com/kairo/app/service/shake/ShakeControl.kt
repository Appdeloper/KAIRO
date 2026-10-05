package com.kairo.app.service.shake

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.kairo.app.KairoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Starting and stopping shake. Callers are visible UI, or the boot/update receiver (an exempt context). */
object ShakeControl {
    /** False if Android refused to start the service here; never throws. */
    fun start(context: Context): Boolean = try {
        ContextCompat.startForegroundService(context, ShakeService.startIntent(context))
        ShakeNotifications.clearRearm(context)
        true
    } catch (e: IllegalStateException) {
        val refused = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException
        Log.w(BriefingLauncher.TAG, "shake start refused (fgsNotAllowed=$refused)", e)
        false
    }

    fun stop(context: Context) {
        context.stopService(ShakeService.startIntent(context))
    }

    /** A restart after Android stopped shake (from the stopped card, the re-arm notification or boot). */
    suspend fun restart(context: Context): Boolean {
        val started = start(context)
        if (started) (context.applicationContext as KairoApp).container.shakePrefsRepository.recordRestart(LocalDate.now().toEpochDay())
        return started
    }
}

/**
 * Re-arms shake after reboot or app update, only if the user turned it on. Both broadcasts are
 * documented exemptions for starting a foreground service, and Android 15's boot-time limits
 * don't cover specialUse. If a ROM refuses anyway, ask the user to tap instead of crashing.
 */
class ShakeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val enabled = (context.applicationContext as KairoApp).container.shakePrefsRepository.current().enabled
                if (enabled && !ShakeControl.restart(context)) ShakeNotifications.postRearm(context)
            } finally {
                pending.finish()
            }
        }
    }
}
