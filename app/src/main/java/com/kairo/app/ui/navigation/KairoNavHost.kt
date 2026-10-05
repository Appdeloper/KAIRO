package com.kairo.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kairo.app.R
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.onboarding.OnboardingScreen
import com.kairo.app.ui.onboarding.ProfileViewModel
import com.kairo.app.ui.settings.SettingsScreen
import com.kairo.app.ui.tasks.TasksScreen
import com.kairo.app.ui.timetable.TimetableScreen
import com.kairo.app.ui.today.TodayScreen

enum class TopLevelDestination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    TODAY("today", R.string.nav_today, Icons.Outlined.WbSunny),
    TIMETABLE("timetable", R.string.nav_timetable, Icons.Outlined.CalendarViewWeek),
    TASKS("tasks", R.string.nav_tasks, Icons.Outlined.Checklist),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
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
        prefs?.onboardingDone == false -> OnboardingScreen(profileViewModel)
        else -> MainShell()
    }
}

@Composable
private fun MainShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TopLevelDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                // Standard bottom-bar behavior: one copy per tab, state kept when switching.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.TODAY.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.TODAY.route) { TodayScreen() }
            composable(TopLevelDestination.TIMETABLE.route) { TimetableScreen() }
            composable(TopLevelDestination.TASKS.route) { TasksScreen() }
            composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
