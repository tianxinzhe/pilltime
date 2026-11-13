package com.alex.pilltime.data.repo

import com.alex.pilltime.data.db.dao.SettingsDao
import com.alex.pilltime.data.entity.AppSettings
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 设置仓库。无数据时使用默认值，不阻塞。
 */
class SettingsRepository(private val dao: SettingsDao) {

    val settings: Flow<AppSettings> = dao.observe().map { it ?: AppSettings() }

    suspend fun get(): AppSettings = dao.get() ?: AppSettings()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dao.upsert(transform(get()))
    }

    /** pausedUntil 为空表示恢复；非空表示暂停至该时刻。 */
    suspend fun setPaused(pausedUntil: Instant?) {
        update { it.copy(allPaused = pausedUntil != null, pausedUntil = pausedUntil) }
    }

    suspend fun setActiveProfile(id: Long) {
        update { it.copy(activeProfileId = id) }
    }

    suspend fun setLanguage(tag: String) {
        update { it.copy(appLanguage = tag) }
    }
}