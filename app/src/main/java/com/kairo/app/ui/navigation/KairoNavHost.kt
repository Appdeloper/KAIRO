package com.kairo.app.ui.navigation

import com.kairo.app.ui.beta.BetaPrompts
import com.kairo.app.util.beta.Events
import com.kairo.app.util.beta.BetaEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kairo.app.R
import com.kairo.app.ui.alarms.AlarmsScreen
import com.kairo.app.ui.briefing.BriefingActivity
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.MinTouchTarget
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.Stroke
import com.kairo.app.ui.design.components.KairoScaffold
import com.kairo.app.ui.design.components.LoadingOrb
import com.kairo.app.ui.design.components.glass
import com.kairo.app.ui.design.Elevation
import com.kairo.app.ui.onboarding.OnboardingScreen
import com.kairo.app.ui.onboarding.ProfileViewModel
import com.kairo.app.ui.plan.PlanScreen
import com.kairo.app.ui.plan.PlanSegment
import com.kairo.app.ui.settings.SettingsScreen
import com.kairo.app.ui.today.TodayScreen

enum class TopLevelDestination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    TODAY("today", R.string.nav_today, Icons.Outlined.WbSunny),
    PLAN("plan", R.string.nav_plan, Icons.Outlined.CalendarViewWeek),
    ALARMS("alarms", R.string.nav_alarms, Icons.Outlined.Alarm),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

/** Plan opens on a chosen segment, e.g. "plan?segment=TIMETABLE" from Today's empty state. */
object PlanRoute {
    const val ARG = "segment"
    const val PATTERN = "plan?$ARG={$ARG}"
    fun of(segment: PlanSegment) = "plan?$ARG=${segment.name}"
    fun parse(value: String?): PlanSegment = PlanSegment.entries.firstOrNull { it.name == value } ?: PlanSegment.TIMETABLE
}

/**
 * Onboarding gates the whole app rather than being a nav destination: once the profile is saved,
 * the DataStore flow flips and the main shell replaces it, so back can't return to onboarding.
 */
@Composable
fun KairoRoot(
    profileViewModel: ProfileViewModel = viewModel(
        factory = containerFactory { ProfileViewModel(it.userPrefsRepository, it.roleRepository) },
    ),
) {
    val prefs by profileViewModel.prefs.collectAsStateWithLifecycle()
    when {
        prefs == null -> Box(Modifier.fillMaxSize()) // DataStore not read yet; avoids an onboarding flash.
        prefs?.onboardingDone == false -> OnboardingScreen()
        else -> MainShell()
    }
}

@Composable
private fun MainShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current

    KairoScaffold(
        bottomBar = {
            KairoBottomBar(
                current = backStackEntry?.destination,
                onSelect = { tab ->
                    Events.record(BetaEvent.valueOf("TAB_${tab.name}"))
                    navController.navigateToTab(tab)
                },
                onOrb = { context.startActivity(BriefingActivity.voiceIntent(context)) },
            )
        },
    ) { padding ->
        BetaPrompts()
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.TODAY.route,
            // Consuming tells each screen's own Scaffold these insets are handled, so it doesn't pad them twice.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(TopLevelDestination.TODAY.route) {
                TodayScreen(
                    onOpenAlarms = { navController.navigateToTab(TopLevelDestination.ALARMS) },
                    onOpenPlan = { segment -> navController.navigate(PlanRoute.of(segment)) { launchSingleTop = true } },
                )
            }
            composable(
                PlanRoute.PATTERN,
                arguments = listOf(navArgument(PlanRoute.ARG) { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry -> PlanScreen(initialSegment = PlanRoute.parse(entry.arguments?.getString(PlanRoute.ARG))) }
            composable(TopLevelDestination.ALARMS.route) { AlarmsScreen() }
            composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}

/** Standard bottom-bar behaviour: one copy per tab, state kept when switching. */
private fun NavHostController.navigateToTab(dest: TopLevelDestination) {
    // "plan" matches the Plan pattern with its optional segment argument left out.
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavDestination?.isTab(dest: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.route == dest.route || (dest == TopLevelDestination.PLAN && it.route == PlanRoute.PATTERN) } == true

private val BarHeight = 72.dp
private val OrbButtonSize = 68.dp

/**
 * Glass bottom bar: two tabs, a gap, two tabs. The orb button floats over the gap on every tab and
 * opens the briefing ready to listen.
 */
@Composable
fun KairoBottomBar(current: NavDestination?, onSelect: (TopLevelDestination) -> Unit, onOrb: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KairoTheme.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .fillMaxWidth()
                .glass(colors.surface1, androidx.compose.foundation.shape.RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg), Elevation.RAISED)
                .navigationBarsPadding()
                .height(BarHeight)
                .padding(horizontal = Spacing.sm)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (left, right) = TopLevelDestination.entries.chunked(2)
            left.forEach { BarItem(it, current.isTab(it), onSelect) }
            Spacer(Modifier.width(OrbButtonSize + Spacing.lg))
            right.forEach { BarItem(it, current.isTab(it), onSelect) }
        }
        OrbButton(onOrb, Modifier.align(Alignment.TopCenter).offset(y = -(OrbButtonSize / 3)))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarItem(dest: TopLevelDestination, selected: Boolean, onSelect: (TopLevelDestination) -> Unit) {
    val colors = KairoTheme.colors
    Column(
        Modifier
            .weight(1f)
            .defaultMinSize(minHeight = MinTouchTarget)
            .clip(Radius.medium)
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(dest) })
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(
            Modifier
                .clip(Radius.full)
                .then(if (selected) Modifier.background(colors.tint(colors.primary, 0.18f)) else Modifier)
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        ) {
            Icon(dest.icon, contentDescription = null, tint = if (selected) colors.primary else colors.textSecondary)
        }
        Text(stringResource(dest.label), style = KairoTheme.type.labelMedium, color = if (selected) colors.textPrimary else colors.textSecondary, maxLines = 1)
    }
}

/** The always-there voice entry: the brand orb, softly breathing, with a glass rim. */
@Composable
fun OrbButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KairoTheme.colors
    val label = stringResource(R.string.orb_button_label)
    Box(
        modifier
            .size(OrbButtonSize)
            .clip(Radius.full)
            .background(colors.background)
            .border(Stroke.hairline, colors.outlineStrong, Radius.full)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        LoadingOrb(size = OrbButtonSize)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 160)
@Composable
private fun KairoBottomBarPreview() {
    KairoTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            KairoBottomBar(current = null, onSelect = {}, onOrb = {})
        }
    }
}
