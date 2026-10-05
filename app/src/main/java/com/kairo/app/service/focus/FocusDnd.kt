package com.kairo.app.service.focus

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.kairo.app.data.prefs.FocusPrefsRepository
import com.kairo.app.domain.focus.DndRules

/**
 * Optional DND during focus. What we switched on is written to DataStore before returning, so a
 * later process (the end alarm, the boot receiver, app open) can restore it after process death.
 */
class FocusDnd(private val context: Context, private val prefs: FocusPrefsRepository) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun hasAccess(): Boolean = manager.isNotificationPolicyAccessGranted

    suspend fun applyIfWanted() {
        if (!prefs.current().silenceDuringFocus || !hasAccess()) return
        try {
            when (val plan = DndRules.planApply(Build.VERSION.SDK_INT, manager.currentInterruptionFilter, priorityAllowsAlarms())) {
                DndRules.Apply.ActivateAppRule -> {
                    // On 35+ this policy only shapes KAIRO's own rule, never the user's global settings.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) manager.setNotificationPolicy(focusPolicy())
                    manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    prefs.recordDndApplied(previousFilter = null, appliedFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }
                is DndRules.Apply.SetFilter -> {
                    prefs.recordDndApplied(plan.previous, plan.filter)
                    manager.setInterruptionFilter(plan.filter)
                }
                DndRules.Apply.Skip -> Unit
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "DND access revoked between check and use", e)
        }
    }

    suspend fun restore() {
        val s = prefs.current()
        val plan = DndRules.planRestore(Build.VERSION.SDK_INT, s.dndApplied, s.dndAppliedFilter, s.dndPreviousFilter, manager.currentInterruptionFilter)
        try {
            when (plan) {
                DndRules.Restore.DeactivateAppRule -> manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                is DndRules.Restore.SetFilter -> manager.setInterruptionFilter(plan.filter)
                DndRules.Restore.Nothing -> Unit
            }
        } catch (e: SecurityException) {
            // Access was revoked; Android removes an app's DND rules with it, so there's nothing left to undo.
            Log.w(TAG, "DND access revoked before restore", e)
        }
        if (s.dndApplied) prefs.clearDndApplied()
    }

    /** API 28 added the alarms toggle; before that, priority mode always let alarms through. */
    private fun priorityAllowsAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            manager.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0

    /** Alarms and media always; calls from starred contacts and repeat callers, so emergencies get through. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun focusPolicy() = NotificationManager.Policy(
        NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
            NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
            NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
            NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS,
        NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
        NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
    )

    companion object {
        private const val TAG = "KairoFocus"
        fun accessSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }
}
