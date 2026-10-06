package com.kairo.app.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.kairo.app.R
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.SegmentedControl
import com.kairo.app.ui.design.components.TopBar
import com.kairo.app.ui.tasks.TasksScreen
import com.kairo.app.ui.timetable.TimetableScreen

enum class PlanSegment { TIMETABLE, TASKS }

/** Plan = the week's fixed blocks and the task list, two halves of the same job. */
@Composable
fun PlanScreen(initialSegment: PlanSegment = PlanSegment.TIMETABLE) {
    var segment by rememberSaveable(initialSegment) { mutableStateOf(initialSegment) }
    PlanFrame(segment, onSegment = { segment = it }) {
        when (segment) {
            PlanSegment.TIMETABLE -> TimetableScreen()
            PlanSegment.TASKS -> TasksScreen()
        }
    }
}

@Composable
fun PlanFrame(segment: PlanSegment, onSegment: (PlanSegment) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.nav_plan))
        SegmentedControl(
            options = listOf(stringResource(R.string.nav_timetable), stringResource(R.string.nav_tasks)),
            selectedIndex = segment.ordinal,
            onSelect = { onSegment(PlanSegment.entries[it]) },
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 200)
@Composable
private fun PlanFramePreview() {
    KairoTheme { PlanFrame(PlanSegment.TASKS, {}) {} }
}
