package com.alex.pilltime.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.alex.pilltime.ui.export.ExportRoute
import com.alex.pilltime.ui.history.HistoryRoute
import com.alex.pilltime.ui.medicine.MedicineEditRoute
import com.alex.pilltime.ui.medicine.MedicineListRoute
import com.alex.pilltime.ui.medicine.ScheduleEditRoute
import com.alex.pilltime.ui.medicine.ScheduleListRoute
import com.alex.pilltime.ui.profile.ProfileRoute
import com.alex.pilltime.ui.settings.SettingsRoute
import com.alex.pilltime.ui.stock.StockRoute
import com.alex.pilltime.ui.today.TodayRoute

/**
 * 路由名固定下来，后续任务直接往对应 Composable 里填内容。
 */
object AppRoutes {
    const val TODAY = "today"
    const val MEDICINES = "medicines"
    const val SCHEDULES = "schedules"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val MEDICINE_EDIT = "medicine_edit/{medicineId}"
    const val STOCK = "stock/{medicineId}"
    const val SCHEDULE_EDIT = "schedule_edit/{medicineId}/{scheduleId}"
    const val EXPORT = "export"
    const val PROFILES = "profiles"

    const val ARG_MEDICINE_ID = "medicineId"
    const val ARG_SCHEDULE_ID = "scheduleId"

    fun medicineEdit(medicineId: Long): String = "medicine_edit/$medicineId"

    fun stock(medicineId: Long): String = "stock/$medicineId"

    fun scheduleEdit(medicineId: Long, scheduleId: Long): String =
        "schedule_edit/$medicineId/$scheduleId"
}

/**
 * 今日首页、药品列表与药品/计划编辑已接通，库存与其余页面先占位。
 */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.TODAY,
        modifier = modifier
    ) {
        composable(AppRoutes.TODAY) {
            TodayRoute(onNavigateToMedicines = {
                navController.navigate(AppRoutes.MEDICINES) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            })
        }
        composable(AppRoutes.MEDICINES) {
            MedicineListRoute(
                onAdd = { navController.navigate(AppRoutes.medicineEdit(0L)) },
                onOpenMedicine = { medicineId ->
                    navController.navigate(AppRoutes.medicineEdit(medicineId))
                },
                onOpenStock = { medicineId ->
                    navController.navigate(AppRoutes.stock(medicineId))
                }
            )
        }
        composable(AppRoutes.SCHEDULES) {
            ScheduleListRoute(
                onEditSchedule = { medicineId, scheduleId ->
                    navController.navigate(AppRoutes.scheduleEdit(medicineId, scheduleId))
                },
                onNavigateToMedicines = {
                    navController.navigate(AppRoutes.MEDICINES) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(AppRoutes.HISTORY) {
            HistoryRoute(onExport = { navController.navigate(AppRoutes.EXPORT) })
        }
        composable(AppRoutes.SETTINGS) {
            SettingsRoute(
                onNavigateToExport = { navController.navigate(AppRoutes.EXPORT) },
                onNavigateToProfiles = { navController.navigate(AppRoutes.PROFILES) }
            )
        }
        composable(AppRoutes.PROFILES) {
            ProfileRoute(onBack = { navController.popBackStack() })
        }
        composable(
            route = AppRoutes.MEDICINE_EDIT,
            arguments = listOf(
                navArgument(AppRoutes.ARG_MEDICINE_ID) { type = NavType.LongType }
            )
        ) { entry ->
            val medicineId = entry.arguments?.getLong(AppRoutes.ARG_MEDICINE_ID) ?: 0L
            MedicineEditRoute(
                medicineId = medicineId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = AppRoutes.STOCK,
            arguments = listOf(
                navArgument(AppRoutes.ARG_MEDICINE_ID) { type = NavType.LongType }
            )
        ) { entry ->
            val medicineId = entry.arguments?.getLong(AppRoutes.ARG_MEDICINE_ID) ?: 0L
            StockRoute(
                medicineId = medicineId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = AppRoutes.SCHEDULE_EDIT,
            arguments = listOf(
                navArgument(AppRoutes.ARG_MEDICINE_ID) { type = NavType.LongType },
                navArgument(AppRoutes.ARG_SCHEDULE_ID) { type = NavType.LongType }
            )
        ) { entry ->
            val medicineId = entry.arguments?.getLong(AppRoutes.ARG_MEDICINE_ID) ?: 0L
            val scheduleId = entry.arguments?.getLong(AppRoutes.ARG_SCHEDULE_ID) ?: 0L
            ScheduleEditRoute(
                medicineId = medicineId,
                scheduleId = scheduleId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.EXPORT) {
            ExportRoute(onBack = { navController.popBackStack() })
        }
    }
}