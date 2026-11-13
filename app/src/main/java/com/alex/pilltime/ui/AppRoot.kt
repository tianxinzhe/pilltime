package com.alex.pilltime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alex.pilltime.R
import com.alex.pilltime.ServiceLocator
import com.alex.pilltime.data.entity.AppSettings
import com.alex.pilltime.ui.nav.AppNavHost
import com.alex.pilltime.ui.nav.AppRoutes
import com.alex.pilltime.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.launch

/** 底部导航的一个 tab。 */
private data class BottomTab(val route: String, val labelRes: Int, val icon: ImageVector)

private val BOTTOM_TABS = listOf(
    BottomTab(AppRoutes.TODAY, R.string.tab_today, Icons.Filled.Home),
    BottomTab(AppRoutes.MEDICINES, R.string.tab_medicines, Icons.AutoMirrored.Filled.List),
    BottomTab(AppRoutes.HISTORY, R.string.tab_history, Icons.Filled.DateRange),
    BottomTab(AppRoutes.SETTINGS, R.string.tab_settings, Icons.Filled.Settings)
)

/**
 * 应用根布局：首次启动先显示引导页（不显示底部导航），完成后进入主界面。
 */
@Composable
fun AppRoot() {
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsState(initial = null as AppSettings?)
    val scope = rememberCoroutineScope()
    val current = settings
    when {
        current == null -> Box(modifier = Modifier.fillMaxSize())
        !current.onboardingDone -> OnboardingScreen(
            onDone = {
                scope.launch {
                    ServiceLocator.settingsRepository.update { it.copy(onboardingDone = true) }
                }
            }
        )

        else -> MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { AppBottomBar(navController = navController) }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/** tab 切换保留各自页面状态，且不会在返回栈里堆积重复页面。 */
@Composable
private fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    NavigationBar {
        for (tab in BOTTOM_TABS) {
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = { Text(text = stringResource(tab.labelRes)) }
            )
        }
    }
}