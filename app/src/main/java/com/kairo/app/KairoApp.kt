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
import com.kairo.app.ui.today.DayTimelineSource
import com.kairo.app.ui.widget.KairoWidget
import com.kairo.app.ui.widget.WidgetRefreshWorker

private const val WIDGET_DEBOUNCE_MS = 500L

class KairoApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Outlives any screen, so first-launch seeding isn't cancelled by a quick rotation or back press. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.roleRepository.seedDefaultsIfEmpty() }
        appScope.launch { WidgetRefreshWorker.ensureScheduledIfPlaced(this@KairoApp) }
        refreshWidgetOnPlanChanges()
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
