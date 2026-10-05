package com.kairo.app.domain.focus

import android.app.NotificationManager
import com.kairo.app.domain.focus.DndRules.Apply
import com.kairo.app.domain.focus.DndRules.Restore
import org.junit.Assert.assertEquals
import org.junit.Test

class DndRulesTest {
    @Test
    fun constantsMatchTheFramework() {
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, DndRules.FILTER_ALL)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, DndRules.FILTER_PRIORITY)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, DndRules.FILTER_ALARMS)
    }

    @Test
    fun android15Plus_usesTheAppsOwnRule() {
        assertEquals(Apply.ActivateAppRule, DndRules.planApply(35, DndRules.FILTER_PRIORITY, priorityAllowsAlarms = false))
        assertEquals(Restore.DeactivateAppRule, DndRules.planRestore(37, applied = true, appliedFilter = 2, previousFilter = null, currentFilter = 2))
    }

    @Test
    fun older_priorityWhenItLetsAlarmsThrough_elseAlarmsOnly() {
        assertEquals(Apply.SetFilter(DndRules.FILTER_PRIORITY, previous = DndRules.FILTER_ALL), DndRules.planApply(33, DndRules.FILTER_ALL, true))
        assertEquals(Apply.SetFilter(DndRules.FILTER_ALARMS, previous = DndRules.FILTER_ALL), DndRules.planApply(33, DndRules.FILTER_ALL, false))
    }

    @Test
    fun older_leavesUsersOwnDndAlone() {
        assertEquals(Apply.Skip, DndRules.planApply(30, DndRules.FILTER_PRIORITY, true))
    }

    @Test
    fun older_restoresPreviousOnlyIfStillOurs() {
        assertEquals(Restore.SetFilter(DndRules.FILTER_ALL), DndRules.planRestore(33, true, appliedFilter = 2, previousFilter = 1, currentFilter = 2))
        // The user changed DND during the session: theirs wins.
        assertEquals(Restore.Nothing, DndRules.planRestore(33, true, appliedFilter = 2, previousFilter = 1, currentFilter = 3))
        assertEquals(Restore.Nothing, DndRules.planRestore(33, applied = false, appliedFilter = null, previousFilter = null, currentFilter = 2))
    }
}
