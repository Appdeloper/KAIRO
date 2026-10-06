package com.kairo.app.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.currentLocale
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.Stroke
import com.kairo.app.ui.design.components.DayPicker
import com.kairo.app.ui.design.components.EmptyState
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.LoadingOrb
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.TimelineItem
import com.kairo.app.ui.design.laneStyle
import com.kairo.app.util.formatMinuteOfDay
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = viewModel(
        factory = containerFactory { TimetableViewModel(it.timetableRepository, it.roleRepository, it.userPrefsRepository.prefs.map { p -> p.hiddenRoleIds }) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TimetableContent(state = state, onSave = viewModel::save, onDelete = viewModel::delete)
}

/**
 * Phones: a day picker and that day's classes as a list (readable, tappable). Wide screens (600 dp+):
 * the whole week as a grid, days as columns.
 */
@Composable
fun TimetableContent(
    state: TimetableUiState,
    onSave: (FixedBlock) -> Unit,
    onDelete: (FixedBlock) -> Unit,
    modifier: Modifier = Modifier,
    today: Int = LocalDate.now().dayOfWeek.value,
    forceWide: Boolean? = null,
) {
    // Null = closed. A block with id 0 means "new"; anything else is an edit.
    var editing by remember { mutableStateOf<FixedBlock?>(null) }
    var day by rememberSaveable { mutableIntStateOf(today) }
    val defaultRoleId = state.roles.firstOrNull { it.id !in state.hiddenRoleIds }?.id ?: state.roles.firstOrNull()?.id ?: 0L

    fun newBlock(onDay: Int, startMinute: Int) = FixedBlock(
        title = "",
        roleId = defaultRoleId,
        dayOfWeek = onDay,
        startMinute = startMinute,
        endMinute = (startMinute + 60).coerceAtMost(23 * 60 + 59),
    )

    if (!state.loaded) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingOrb() }
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = forceWide ?: (maxWidth >= WIDE_BREAKPOINT)
        if (wide) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.timetable_week), Modifier.weight(1f))
                    KairoTextButton(stringResource(R.string.timetable_add), { editing = newBlock(day, 9 * 60) })
                }
                WeekGrid(
                    blocks = state.blocks,
                    roleColors = state.roles.associate { it.id to it },
                    today = today,
                    onEmptySlotTap = { d, minute -> editing = newBlock(d, minute) },
                    onBlockTap = { editing = it },
                )
            }
        } else {
            DayList(
                blocks = state.blocks.filter { it.dayOfWeek == day }.sortedBy { it.startMinute },
                state = state,
                day = day,
                today = today,
                onDay = { day = it },
                onAdd = { editing = newBlock(day, 9 * 60) },
                onEdit = { editing = it },
            )
        }
    }

    editing?.let { block ->
        BlockEditorSheet(
            initial = block,
            roles = state.roles,
            hiddenRoleIds = state.hiddenRoleIds,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = { onDelete(it); editing = null },
        )
    }
}

@Composable
private fun DayList(
    blocks: List<FixedBlock>,
    state: TimetableUiState,
    day: Int,
    today: Int,
    onDay: (Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (FixedBlock) -> Unit,
) {
    val context = LocalContext.current
    val rolesById = state.roles.associateBy { it.id }
    val dayName = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, currentLocale())
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.bottomBarClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "days") { DayPicker(selected = setOf(day), onToggle = onDay, marked = today) }
        item(key = "header") {
            SectionHeader(dayName) {
                KairoTextButton(stringResource(R.string.timetable_add), onAdd)
            }
        }
        if (blocks.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.timetable_day_empty_title, dayName),
                    body = stringResource(R.string.timetable_day_empty_body),
                    actionLabel = stringResource(R.string.timetable_add),
                    onAction = onAdd,
                )
            }
        }
        items(blocks, key = { it.id }) { block ->
            val role = rolesById[block.roleId]
            TimelineItem(
                startTime = formatMinuteOfDay(context, block.startMinute),
                endTime = formatMinuteOfDay(context, block.endMinute),
                title = block.title,
                laneColor = laneStyle(role).color,
                laneLabel = role?.name,
                detail = block.location,
                onClick = { onEdit(block) },
                onClickLabel = stringResource(R.string.timetable_edit_block),
            )
        }
    }
}

@Composable
private fun WeekGrid(
    blocks: List<FixedBlock>,
    roleColors: Map<Long, com.kairo.app.data.local.Role>,
    today: Int,
    onEmptySlotTap: (day: Int, minute: Int) -> Unit,
    onBlockTap: (FixedBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Grow the visible range to fit early or late classes rather than hiding them.
    val firstHour = minOf(DEFAULT_FIRST_HOUR, blocks.minOfOrNull { it.startMinute / 60 } ?: DEFAULT_FIRST_HOUR)
    val lastHour = maxOf(DEFAULT_LAST_HOUR, blocks.maxOfOrNull { (it.endMinute + 59) / 60 } ?: DEFAULT_LAST_HOUR)
    val hours = firstHour until lastHour
    val context = LocalContext.current
    val colors = KairoTheme.colors

    Column(modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
            Box(Modifier.width(HourLabelWidth))
            DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.SHORT, currentLocale()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = KairoTheme.type.labelLarge,
                    color = if (d.value == today) colors.moment else colors.textSecondary,
                )
            }
        }
        Row(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(Modifier.width(HourLabelWidth)) {
                hours.forEach { hour ->
                    Text(
                        formatMinuteOfDay(context, hour * 60),
                        modifier = Modifier.height(HourHeight),
                        style = KairoTheme.numbers.small,
                        color = colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
            DayOfWeek.entries.forEach { d ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(HourHeight * hours.count())
                        .border(Stroke.hairline / 2, colors.outline)
                        .pointerInput(d, firstHour) {
                            detectTapGestures { offset ->
                                // Snap to the tapped hour: precise times are set in the editor anyway.
                                val hour = firstHour + (offset.y / HourHeight.toPx()).toInt()
                                onEmptySlotTap(d.value, hour.coerceIn(0, 23) * 60)
                            }
                        },
                ) {
                    blocks.filter { it.dayOfWeek == d.value }.forEach { block ->
                        val lane = laneStyle(roleColors[block.roleId])
                        Box(
                            Modifier
                                .offset(y = HourHeight * ((block.startMinute - firstHour * 60) / 60f))
                                .height(HourHeight * ((block.endMinute - block.startMinute) / 60f))
                                .fillMaxWidth()
                                .padding(Spacing.xxs)
                                .clip(Radius.small)
                                .background(lane.tint)
                                .border(Stroke.hairline, lane.color, Radius.small)
                                .clickable { onBlockTap(block) }
                                .padding(Spacing.xs),
                        ) {
                            Text(block.title, style = KairoTheme.type.labelMedium, color = colors.textPrimary, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

private val WIDE_BREAKPOINT = 600.dp
private val HourLabelWidth = 56.dp
private val HourHeight = 56.dp
private const val DEFAULT_FIRST_HOUR = 8
private const val DEFAULT_LAST_HOUR = 20

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 720)
@Composable
private fun TimetablePhonePreview() {
    KairoTheme { TimetableContent(state = PreviewData.timetableState.copy(loaded = true), onSave = {}, onDelete = {}, today = 1) }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, widthDp = 840, heightDp = 600)
@Composable
private fun TimetableWidePreview() {
    KairoTheme { TimetableContent(state = PreviewData.timetableState.copy(loaded = true), onSave = {}, onDelete = {}, today = 2) }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun TimetableEmptyDayPreview() {
    KairoTheme { TimetableContent(state = PreviewData.timetableState.copy(loaded = true), onSave = {}, onDelete = {}, today = 6) }
}
