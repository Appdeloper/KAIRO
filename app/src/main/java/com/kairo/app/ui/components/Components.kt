package com.kairo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.text.format.DateFormat
import com.kairo.app.R
import com.kairo.app.data.local.Role
import com.kairo.app.util.parseHexColor

@Composable
fun RoleDot(role: Role?, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier
            .size(size)
            .background(role?.let { parseHexColor(it.colorHex) } ?: Color.Gray, CircleShape),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RolePicker(roles: List<Role>, selectedId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        roles.forEach { role ->
            val color = parseHexColor(role.colorHex)
            FilterChip(
                selected = role.id == selectedId,
                onClick = { onSelect(role.id) },
                label = { Text(role.name) },
                leadingIcon = { RoleDot(role) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.22f),
                    selectedLabelColor = color,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

/** Locale read through composition so labels re-render if the user switches app language. */
@Composable
fun currentLocale(): java.util.Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
