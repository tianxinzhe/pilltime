package com.medtimer.app

import android.app.Application
import com.medtimer.app.alarm.NotificationHelper

class MedTimerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化全局依赖入口
        ServiceLocator.init(this)
        // 提前创建通知渠道，语言变化后名称随渠道重建更新
        NotificationHelper(this).ensureChannel()
    }
}