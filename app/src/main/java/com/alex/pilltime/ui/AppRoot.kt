package com.alex.pilltime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
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

/**
 * 底部导航的一个 tab：每个 tab 一个专属色。
 * 核心图标直接用 ImageVector；胶囊/时钟等不在核心图标集里的用自绘矢量。
 */
private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val color: Color,
    val vectorIcon: ImageVector? = null,
    val iconRes: Int? = null
)

/** 今日/药品/计划/历史/设置：蓝、绿、橙、紫、青。 */
private val BOTTOM_TABS = listOf(
    BottomTab(
        route = AppRoutes.TODAY,
        labelRes = R.string.tab_today,
        color = Color(0xFF1E88E5),
        vectorIcon = Icons.Filled.Home
    ),
    BottomTab(
        route = AppRoutes.MEDICINES,
        labelRes = R.string.tab_medicines,
        color = Color(0xFF43A047),
        iconRes = R.drawable.ic_tab_medicine
    ),
    BottomTab(
        route = AppRoutes.SCHEDULES,
        labelRes = R.string.tab_schedules,
        color = Color(0xFFFB8C00),
        vectorIcon = Icons.Filled.DateRange
    ),
    BottomTab(
        route = AppRoutes.HISTORY,
        labelRes = R.string.tab_history,
        color = Color(0xFF7E57C2),
        iconRes = R.drawable.ic_tab_history
    ),
    BottomTab(
        route = AppRoutes.SETTINGS,
        labelRes = R.string.tab_settings,
        color = Color(0xFF00897B),
        vectorIcon = Icons.Filled.Settings
    )
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

/**
 * tab 切换保留各自页面状态，且不会在返回栈里堆积重复页面。
 * 图标用专属色：选中满色 + 淡色指示底，未选中半透明同色。
 */
@Composable
private fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    NavigationBar {
        for (tab in BOTTOM_TABS) {
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    TabIcon(
                        tab = tab,
                        tint = if (selected) tab.color else tab.color.copy(alpha = 0.55f)
                    )
                },
                label = { Text(text = stringResource(tab.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = tab.color.copy(alpha = 0.16f)
                )
            )
        }
    }
}

@Composable
private fun TabIcon(tab: BottomTab, tint: Color, modifier: Modifier = Modifier) {
    if (tab.vectorIcon != null) {
        Icon(
            imageVector = tab.vectorIcon,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    } else {
        Icon(
            painter = painterResource(tab.iconRes!!),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}

/** 图标预览：放大到 48dp 检查形状与配色。 */
@Preview(showBackground = true)
@Composable
private fun BottomTabIconsPreview() {
    Row(
        modifier = Modifier.padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        for (tab in BOTTOM_TABS) {
            TabIcon(tab = tab, tint = tab.color, modifier = Modifier.size(48.dp))
        }
    }
}