package com.kairo.app.ai

import com.kairo.app.data.local.TaskStatus
import com.kairo.app.domain.plan.PlanState
import java.time.LocalDate
import java.util.Locale

/** The planner state plus the one profile field the Worker needs. */
data class PlannerSnapshot(val state: PlanState, val firstName: String)

/**
 * Builds the smallest context the model needs (rule 8): titles, role names and times for today and
 * tomorrow. No ids, deadlines, notes or anything older, and every string is stripped of PII.
 */
object ContextBuilder {
    private const val MAX_TITLE = 120
    private const val MAX_NAME = 40
    private const val MAX_ROLES = 12
    private const val MAX_ITEMS = 60
    private const val FALLBACK_NAME = "friend"

    fun build(snapshot: PlannerSnapshot): PlannerContextDto {
        val state = snapshot.state
        val roleNames = state.roles.associate { it.id to clean(it.name, MAX_NAME) }
        fun itemsOn(date: LocalDate, day: String): List<ContextItemDto> {
            val lectures = state.blocksOn(date).map {
                ContextItemDto(clean(it.title, MAX_TITLE), roleNames[it.roleId], day, it.startMinute, it.endMinute, "lecture")
            }
            val tasks = state.tasksOn(date)
                .filter { it.status == TaskStatus.TODO || it.status == TaskStatus.SCHEDULED }
                .map { t ->
                    ContextItemDto(
                        clean(t.title, MAX_TITLE), roleNames[t.roleId], day,
                        t.scheduledStartMinute, t.scheduledStartMinute?.plus(t.durationMinutes), "task",
                    )
                }
            return lectures + tasks
        }
        return PlannerContextDto(
            firstName = clean(snapshot.firstName, MAX_NAME).ifBlank { FALLBACK_NAME },
            localDate = state.today.toString(),
            localTime = String.format(Locale.ROOT, "%02d:%02d", state.nowMinute / 60, state.nowMinute % 60),
            roles = roleNames.values.filter { it.isNotBlank() }.take(MAX_ROLES),
            items = (itemsOn(state.today, "today") + itemsOn(state.today.plusDays(1), "tomorrow"))
                .filter { it.title.isNotBlank() }
                .take(MAX_ITEMS),
        )
    }

    private fun clean(s: String, max: Int) = PiiStripper.strip(s).trim().take(max)
}
