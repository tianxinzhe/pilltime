package com.medtimer.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.medtimer.app.alarm.ForegroundRecompute
import com.medtimer.app.ui.AppRoot
import com.medtimer.app.ui.theme.MedTimerTheme
import com.medtimer.app.ui.today.TodayViewModel
import com.medtimer.app.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    /** 与首页组合共用同一个 Activity 作用域实例，便于回到前台后刷新。 */
    private val todayViewModel: TodayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedTimerTheme {
                AppRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 每次回到前台补算漏服，再让首页重取今天与当前时刻
        lifecycleScope.launch {
            ForegroundRecompute.run()
            todayViewModel.refresh()
            // 补算后同步桌面小组件
            WidgetUpdater.refreshAll(this@MainActivity)
        }
    }
}