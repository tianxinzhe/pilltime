package com.medtimer.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 今日服药小组件。只做被动刷新，不注册常驻服务。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // App 内部主动更新时使用自定义 action
        if (intent.action == ACTION_REFRESH_WIDGET) {
            refreshAsync(context)
        }
    }

    override fun onEnabled(context: Context) {
        // 首次添加时立即填充数据
        refreshAsync(context)
    }

    override fun onDisabled(context: Context) {
        // 无需常驻资源，最后一个组件移除后不需要额外清理
    }

    private fun refreshAsync(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.refreshAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** 供 App 内部主动刷新小组件。 */
        const val ACTION_REFRESH_WIDGET = "com.medtimer.app.widget.action.REFRESH_WIDGET"
    }
}