package com.kairo.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.data.local.Role as LaneRole
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.currentLocale
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.MinTouchTarget
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.Stroke
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Text input on glass: label above-the-line, error text below, brand cursor and focus colour. */
@Composable
fun KairoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = KairoTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = colors.textTertiary) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailing,
        shape = Radius.medium,
        textStyle = KairoTheme.type.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface3,
            unfocusedContainerColor = colors.surface3,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.primary,
            errorBorderColor = colors.error,
            errorLabelColor = colors.error,
            errorSupportingTextColor = colors.error,
            errorContainerColor = colors.surface3,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A label and a tappable time, e.g. "Starts  9:00 AM". The time uses tabular figures. */
@Composable
fun TimeField(label: String, time: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KairoTheme.colors
    Row(
        modifier
            .defaultMinSize(minHeight = MinTouchTarget + Spacing.sm)
            .clip(Radius.medium)
            .background(colors.surface3)
            .border(Stroke.hairline, colors.outline, Radius.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No clock icon: two of these share a row, and "11:30 PM" needs the width more than the icon does.
        Column(Modifier.weight(1f)) {
            Text(label, style = KairoTheme.type.labelSmall, color = colors.textSecondary)
            Text(time, style = KairoTheme.numbers.medium)
        }
    }
}

/** Lane choice as selectable chips (name + colour). Hidden lanes are left out unless already selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanePicker(roles: List<LaneRole>, selectedId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier, hiddenIds: Set<Long> = emptySet()) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        roles.filter { it.id !in hiddenIds || it.id == selectedId }.forEach { role ->
            RoleChip(role, selected = role.id == selectedId, onClick = { onSelect(role.id) })
        }
    }
}

/** Monday-first day selector. [marked] days get a dot (e.g. today). */
@Composable
fun DayPicker(selected: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier, marked: Int? = null) {
    val colors = KairoTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        DayOfWeek.entries.forEach { day ->
            val on = day.value in selected
            val full = day.getDisplayName(TextStyle.FULL, currentLocale())
            Column(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .clip(Radius.medium)
                    .background(if (on) colors.tint(colors.primary, 0.22f) else colors.surface2)
                    .border(Stroke.hairline, if (on) colors.primary else colors.outline, Radius.medium)
                    .clickable(role = Role.Checkbox, onClickLabel = full) { onToggle(day.value) }
                    .padding(vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.getDisplayName(TextStyle.NARROW, currentLocale()),
                    style = KairoTheme.type.labelLarge,
                    color = if (on) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(Spacing.xxs))
                androidx.compose.foundation.layout.Box(
                    Modifier.size(4.dp).clip(Radius.full).background(if (day.value == marked) colors.moment else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun FormsPreview() {
    KairoTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            KairoTextField("DBMS lecture", {}, "Title")
            KairoTextField("", {}, "Title", error = "Give it a name")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TimeField("Starts", "9:00 AM", {}, Modifier.weight(1f))
                TimeField("Ends", "10:00 AM", {}, Modifier.weight(1f))
            }
            LanePicker(PreviewData.roles, 1, {})
            DayPicker(setOf(1, 2, 3, 4, 5), {}, marked = 2)
        }
    }
}
