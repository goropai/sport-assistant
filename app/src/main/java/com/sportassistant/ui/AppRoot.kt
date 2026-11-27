package com.sportassistant.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sportassistant.AppViewModel
import com.sportassistant.R
import com.sportassistant.data.Exercises
import com.sportassistant.data.PlanAdapter
import com.sportassistant.data.PlanGenerator
import com.sportassistant.data.WeeklyReport
import com.sportassistant.data.Profile
import com.sportassistant.pose.RepTrackers
import java.time.LocalDate

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", R.string.tab_today, AppIcons.Today),
    Tab("walk", R.string.tab_walk, AppIcons.Walk),
    Tab("food", R.string.tab_food, AppIcons.Food),
    Tab("progress", R.string.tab_progress, AppIcons.Progress),
    Tab("settings", R.string.tab_settings, AppIcons.Settings),
)

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val profile by vm.profile.collectAsState()
    when (val p = profile) {
        null -> Box(Modifier.fillMaxSize())
        else -> if (p.onboarded) MainNav(vm, p) else OnboardingScreen(p) { vm.saveProfile(it.copy(onboarded = true)) }
    }
}

@Composable
private fun MainNav(vm: AppViewModel, profile: Profile) {
    val nav = rememberNavController()
    val stats by vm.stats.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val level = PlanAdapter.levelFor(stats.activeDaysPrev6, stats.hadWorkoutBeforeToday, profile.activity)
    val plan = remember(profile.complaints, level) { PlanGenerator.forDay(LocalDate.now().toEpochDay(), profile.complaints, level) }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") {
                val health by vm.health.collectAsState()
                val grant = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refreshHealth() }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshHealth() }
                TodayScreen(
                    plan,
                    stats,
                    level,
                    health,
                    onConnect = { grant.launch(vm.healthPermissions) },
                    onRefresh = vm::refreshHealth,
                    onStart = { nav.navigate("workout") },
                    onOpen = { nav.navigate("exercise/${it.id}") },
                    onLibrary = { nav.navigate("library") },
                )
            }
            composable("food") {
                val entries by vm.foodToday.collectAsState()
                val frequent by vm.frequentFoods.collectAsState()
                val health by vm.health.collectAsState()
                val walks by vm.walks.collectAsState()
                val analysis by vm.analysis.collectAsState()
                val estimate by vm.estimate.collectAsState()
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshHealth() }
                val today = LocalDate.now().toEpochDay()
                FoodScreen(
                    profile,
                    entries,
                    frequent,
                    health,
                    walkKcalToday = walks.filter { it.epochDay == today }.sumOf { it.kcal },
                    analysis = analysis,
                    estimate = estimate,
                    onPhoto = vm::analyzeFood,
                    onSave = vm::saveFood,
                    onDismiss = vm::dismissAnalysis,
                    onDelete = vm::deleteFood,
                    onEstimate = vm::estimateNutrition,
                    onEstimateHandled = vm::clearEstimate,
                    onUpdate = vm::updateFood,
                )
            }
            composable("walk") {
                val track by vm.track.collectAsState()
                val walks by vm.walks.collectAsState()
                WalkScreen(track, walks, profile.weightKg, onStart = vm::startWalk, onFinish = vm::finishWalk)
            }
            composable("library") { LibraryScreen(onOpen = { nav.navigate("exercise/${it.id}") }) }
            composable("progress") {
                val logs by vm.logs.collectAsState()
                val week by vm.weekHealth.collectAsState()
                val eaten by vm.eatenKcalByDay.collectAsState()
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshWeekHealth() }
                val report = remember(logs, week, eaten) { WeeklyReport.build(LocalDate.now().toEpochDay(), logs, week, eaten) }
                ProgressScreen(stats, report, hasWatchData = week != null, onObservations = { nav.navigate("observations") })
            }
            composable("observations") {
                val items by vm.observations.collectAsState()
                val obsState by vm.obsState.collectAsState()
                ObservationsScreen(
                    profile,
                    items,
                    obsState,
                    onPhoto = vm::analyzeObservation,
                    onSave = vm::saveObservation,
                    onDismiss = vm::dismissObservation,
                    onDelete = vm::deleteObservation,
                    onBack = { vm.dismissObservation(); nav.popBackStack() },
                )
            }
            composable("settings") {
                val update by vm.update.collectAsState()
                val backup by vm.backup.collectAsState()
                SettingsScreen(
                    profile,
                    update,
                    onChange = vm::saveProfile,
                    onRedo = { vm.saveProfile(profile.copy(onboarded = false)) },
                    onCheckUpdate = vm::checkForUpdate,
                    onInstall = vm::installUpdate,
                    backup = backup,
                    onExport = vm::exportTo,
                    onReadBackup = vm::readBackup,
                    onConfirmRestore = vm::confirmRestore,
                    onDismissBackup = vm::dismissBackup,
                )
            }
            composable("exercise/{id}") { entry ->
                val ex = Exercises.byId(entry.arguments?.getString("id").orEmpty())
                if (ex != null) {
                    ExerciseDetailScreen(
                        ex,
                        onBack = { nav.popBackStack() },
                        onCamera = { nav.navigate("camera/${it.id}") },
                        onNose = { nav.navigate("nose/${it.id}") },
                    )
                }
            }
            composable("workout") {
                WorkoutScreen(
                    plan,
                    onFinish = vm::logWorkout,
                    onClose = { nav.popBackStack() },
                    onCamera = { nav.navigate("camera/${it.id}") },
                    onNose = { nav.navigate("nose/${it.id}") },
                )
            }
            composable("nose/{id}") { entry ->
                val ex = Exercises.byId(entry.arguments?.getString("id").orEmpty())
                if (ex != null) NoseCountScreen(ex, onDone = { nav.popBackStack() })
            }
            composable("camera/{id}") { entry ->
                val ex = Exercises.byId(entry.arguments?.getString("id").orEmpty())
                val spec = ex?.let { RepTrackers.forExercise(it.id) }
                if (ex != null && spec != null) {
                    CameraScreen(
                        ex,
                        spec,
                        sensitivity = profile.repSensitivity,
                        onSensitivity = { vm.saveProfile(profile.copy(repSensitivity = it)) },
                        onDone = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
