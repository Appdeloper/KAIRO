package com.kairo.app.domain.focus

/**
 * Pure decisions for "Silence notifications during focus". Filter values mirror
 * NotificationManager.INTERRUPTION_FILTER_* (checked in DndRulesTest) so this stays Android-free.
 *
 * Android 15+ (API 35) apps can't change global DND: setInterruptionFilter turns the app's own
 * implicit AutomaticZenRule on or off instead, and the system restores the user's state when we
 * turn it off. Before 35 we change the global filter, so we must remember and restore it ourselves.
 */
object DndRules {
    const val FILTER_ALL = 1
    const val FILTER_PRIORITY = 2
    const val FILTER_ALARMS = 4
    const val APP_RULE_SDK = 35

    sealed interface Apply {
        /** 35+: set our rule's policy (alarms allowed) and activate it with the PRIORITY filter. */
        data object ActivateAppRule : Apply

        /** Before 35: switch the global filter, remembering the one to restore. */
        data class SetFilter(val filter: Int, val previous: Int) : Apply

        /** The user already has DND on; don't touch their choice. */
        data object Skip : Apply
    }

    sealed interface Restore {
        data object DeactivateAppRule : Restore
        data class SetFilter(val filter: Int) : Restore

        /** Nothing of ours is active, or the user changed DND since: leave it alone. */
        data object Nothing : Restore
    }

    /**
     * Wake alarms must still ring. PRIORITY only lets alarms through when the user's priority policy
     * allows them (a toggle since API 28), so fall back to ALARMS-only, which always lets
     * CATEGORY_ALARM through.
     */
    fun planApply(sdkInt: Int, currentFilter: Int, priorityAllowsAlarms: Boolean): Apply = when {
        sdkInt >= APP_RULE_SDK -> Apply.ActivateAppRule
        currentFilter != FILTER_ALL -> Apply.Skip
        priorityAllowsAlarms -> Apply.SetFilter(FILTER_PRIORITY, previous = currentFilter)
        else -> Apply.SetFilter(FILTER_ALARMS, previous = currentFilter)
    }

    fun planRestore(sdkInt: Int, applied: Boolean, appliedFilter: Int?, previousFilter: Int?, currentFilter: Int): Restore = when {
        !applied -> Restore.Nothing
        sdkInt >= APP_RULE_SDK -> Restore.DeactivateAppRule
        currentFilter == appliedFilter -> Restore.SetFilter(previousFilter ?: FILTER_ALL)
        else -> Restore.Nothing
    }
}
