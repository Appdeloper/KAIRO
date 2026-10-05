package com.kairo.app.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.components.currentLocale
import com.kairo.app.ui.theme.KairoTheme
import com.kairo.app.util.formatMinuteOfDay
import com.kairo.app.util.parseHexColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

private val HourHeight = 56.dp
private val HourLabelWidth = 40.dp
private const val DEFAULT_FIRST_HOUR = 7
private const val DEFAULT_LAST_HOUR = 22

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = viewModel(
        factory = containerFactory { TimetableViewModel(it.timetableRepository, it.roleRepository) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TimetableContent(state = state, onSave = viewModel::save, onDelete = viewModel::delete)
}

@Composable
fun TimetableContent(
    state: TimetableUiState,
    onSave: (FixedBlock) -> Unit,
    onDelete: (FixedBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null = closed. A block with id 0 means "new"; anything else is an edit.
    var editing by remember { mutableStateOf<FixedBlock?>(null) }
    val defaultRoleId = state.roles.firstOrNull()?.id ?: 0L

    fun newBlock(day: Int, startMinute: Int) = FixedBlock(
        title = "",
        roleId = defaultRoleId,
        dayOfWeek = day,
        startMinute = startMinute,
        endMinute = (startMinute + 60).coerceAtMost(23 * 60 + 59),
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = newBlock(LocalDate.now().dayOfWeek.value, 9 * 60) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.timetable_add))
            }
        },
    ) { padding ->
        WeekGrid(
            blocks = state.blocks,
            roleColors = state.roles.associate { it.id to parseHexColor(it.colorHex) },
            onEmptySlotTap = { day, minute -> editing = newBlock(day, minute) },
            onBlockTap = { editing = it },
            modifier = Modifier.padding(padding),
        )
    }

    editing?.let { block ->
        BlockEditorSheet(
            initial = block,
            roles = state.roles,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = { onDelete(it); editing = null },
        )
    }
}

@Composable
private fun WeekGrid(
    blocks: List<FixedBlock>,
    roleColors: Map<Long, androidx.compose.ui.graphics.Color>,
    onEmptySlotTap: (day: Int, minute: Int) -> Unit,
    onBlockTap: (FixedBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Grow the visible range to fit early or late classes rather than hiding them.
    val firstHour = minOf(DEFAULT_FIRST_HOUR, blocks.minOfOrNull { it.startMinute / 60 } ?: DEFAULT_FIRST_HOUR)
    val lastHour = maxOf(DEFAULT_LAST_HOUR, blocks.maxOfOrNull { (it.endMinute + 59) / 60 } ?: DEFAULT_LAST_HOUR)
    val hours = firstHour until lastHour
    val context = LocalContext.current
    val today = LocalDate.now().dayOfWeek.value

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Box(Modifier.width(HourLabelWidth))
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, currentLocale()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (day.value == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.width(HourLabelWidth)) {
                hours.forEach { hour ->
                    Text(
                        formatMinuteOfDay(context, hour * 60),
                        modifier = Modifier.height(HourHeight),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            DayOfWeek.entries.forEach { day ->
                DayColumn(
                    day = day.value,
                    blocks = blocks.filter { it.dayOfWeek == day.value },
                    firstHour = firstHour,
                    hourCount = hours.count(),
                    roleColors = roleColors,
                    onEmptySlotTap = onEmptySlotTap,
                    onBlockTap = onBlockTap,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: Int,
    blocks: List<FixedBlock>,
    firstHour: Int,
    hourCount: Int,
    roleColors: Map<Long, androidx.compose.ui.graphics.Color>,
    onEmptySlotTap: (day: Int, minute: Int) -> Unit,
    onBlockTap: (FixedBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier
            .height(HourHeight * hourCount)
            .border(0.5.dp, outline.copy(alpha = 0.4f))
            .pointerInput(day, firstHour) {
                detectTapGestures { offset ->
                    // Snap to the tapped hour: precise times are set in the editor anyway.
                    val hour = firstHour + (offset.y / HourHeight.toPx()).toInt()
                    onEmptySlotTap(day, hour.coerceIn(0, 23) * 60)
                }
            },
    ) {
        blocks.forEach { block ->
            val color = roleColors[block.roleId] ?: MaterialTheme.colorScheme.outline
            val top = HourHeight * ((block.startMinute - firstHour * 60) / 60f)
            val height = HourHeight * ((block.endMinute - block.startMinute) / 60f)
            Box(
                Modifier
                    .offset(y = top)
                    .height(height)
                    .fillMaxWidth()
                    .padding(1.dp)
                    .background(color.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .border(1.dp, color, RoundedCornerShape(6.dp))
                    .clickable { onBlockTap(block) }
                    .padding(2.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    block.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B, heightDp = 720)
@Composable
private fun TimetableContentPreview() {
    KairoTheme {
        TimetableContent(state = PreviewData.timetableState, onSave = {}, onDelete = {})
    }
}
