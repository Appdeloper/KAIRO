package com.kairo.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import androidx.glance.appwidget.updateAll
import com.kairo.app.alarm.AlarmNotifications
import com.kairo.app.alarm.AlarmSync
import com.kairo.app.service.shake.ShakeNotifications
import com.kairo.app.service.focus.FocusNotifications
import com.kairo.app.util.AppVisibility
import com.kairo.app.alarm.isUserUnlocked
import com.kairo.app.ui.today.DayTimelineSource
import com.kairo.app.ui.widget.KairoWidget
import com.kairo.app.ui.widget.WidgetRefreshWorker

private const val WIDGET_DEBOUNCE_MS = 500L
private const val ALARM_SYNC_DEBOUNCE_MS = 200L

class KairoApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Outlives any screen, so first-launch seeding isn't cancelled by a quick rotation or back press. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // First, so a crash anywhere below is still saved for the next launch.
        com.kairo.app.util.beta.BetaSupport.init(this)
        container = AppContainer(this)
        AlarmNotifications.createChannels(this)
        ShakeNotifications.createChannels(this)
        FocusNotifications.createChannels(this)
        AppVisibility.register(this)
        // Direct boot (after a reboot, before first unlock): only alarm code runs, from device-protected
        // storage. Room and DataStore live in encrypted storage and would crash the process here.
        if (!isUserUnlocked()) return
        appScope.launch { container.roleRepository.seedDefaultsIfEmpty() }
        keepAlarmsScheduled()
        appScope.launch { WidgetRefreshWorker.ensureScheduledIfPlaced(this@KairoApp) }
        refreshWidgetOnPlanChanges()
    }

    /** Any alarm, lecture or lecture-skip change re-syncs AlarmManager, whoever made it. */
    @OptIn(FlowPreview::class)
    private fun keepAlarmsScheduled() {
        appScope.launch {
            container.alarmRepository.plans()
                .distinctUntilChanged()
                .debounce(ALARM_SYNC_DEBOUNCE_MS)
                .collect { AlarmSync.syncAll(this@KairoApp) }
        }
    }

    /** Any change to today's plan (Apply, Undo, tick, timetable edit) refreshes the home-screen widget. */
    @OptIn(FlowPreview::class)
    private fun refreshWidgetOnPlanChanges() {
        val timeline = DayTimelineSource(container.taskRepository, container.timetableRepository, container.roleRepository, container.dateProvider)
        appScope.launch {
            timeline.today()
                .map { day -> day.entries }
                .distinctUntilChanged()
                .drop(1) // the first emission is the state at launch, not a change
                .debounce(WIDGET_DEBOUNCE_MS)
                .collect { KairoWidget().updateAll(this@KairoApp) }
        }
    }
}
