package com.kairo.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing

/** Screen frame: brand background, soft-white content colour, consistent insets. */
@Composable
fun KairoScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = KairoTheme.colors
    if (contentWindowInsets != null) {
        Scaffold(modifier, topBar, bottomBar, snackbarHost, floatingActionButton, containerColor = colors.background, contentColor = colors.textPrimary, contentWindowInsets = contentWindowInsets, content = content)
    } else {
        Scaffold(modifier, topBar, bottomBar, snackbarHost, floatingActionButton, containerColor = colors.background, contentColor = colors.textPrimary, content = content)
    }
}

/** Title row for top-level and detail screens. [onBack] adds a back button with a label for TalkBack. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = if (onBack != null) Spacing.xs else Spacing.screen, end = Spacing.sm, top = Spacing.md, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            KairoIconButton(Icons.AutoMirrored.Outlined.ArrowBack, backLabel, onBack, tint = KairoTheme.colors.textPrimary)
            Spacer(Modifier.width(Spacing.xs))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = KairoTheme.type.headlineMedium, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = KairoTheme.type.bodyMedium, color = KairoTheme.colors.textSecondary)
        }
        actions()
    }
}

/** Bottom sheet in the KAIRO style: level-2 glass, short handle, full height (no half state). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KairoBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = KairoTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface2,
        contentColor = colors.textPrimary,
        scrimColor = colors.background.copy(alpha = 0.7f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
        dragHandle = { SheetHandle() },
        content = content,
    )
}

@Composable
fun SheetHandle() {
    Box(Modifier.padding(vertical = Spacing.md).size(width = 40.dp, height = 4.dp).clip(Radius.full).background(KairoTheme.colors.outlineStrong))
}

/** Static frame of a sheet's content for previews and screenshots (ModalBottomSheet needs a window). */
@Composable
fun SheetFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = KairoTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg))
            .background(colors.surface2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetHandle()
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 400)
@Composable
private fun StructurePreview() {
    KairoTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            TopBar("Plan", subtitle = "Week of Oct 5")
            TopBar("Alarm", onBack = {}, backLabel = "Back")
            SheetFrame { Text("Sheet content", modifier = Modifier.padding(Spacing.xl)); Spacer(Modifier.height(Spacing.xl)) }
        }
    }
}
