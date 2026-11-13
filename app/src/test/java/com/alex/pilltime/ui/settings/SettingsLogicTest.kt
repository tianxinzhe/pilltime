package com.alex.pilltime.ui.settings

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置页纯逻辑测试：固定 now 与固定时区，不依赖系统时钟。
 */
class SettingsLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    // 2026-11-13 10:00 +08:00
    private val now: Instant = Instant.parse("2026-11-13T02:00:00Z")

    @Test
    fun pauseOneHourAddsFixedHour() {
        val until = pauseUntil(now, zone, PauseOption.ONE_HOUR)
        assertEquals(Instant.parse("2026-11-13T03:00:00Z"), until)
    }

    @Test
    fun pauseUntilEndOfTodayUsesNextLocalMidnight() {
        val until = pauseUntil(now, zone, PauseOption.END_OF_TODAY)
        // 次日 00:00 +08:00 = 前一天 16:00 UTC
        assertEquals(Instant.parse("2026-11-13T16:00:00Z"), until)
    }

    @Test
    fun resumeReturnsNull() {
        assertNull(pauseUntil(now, zone, PauseOption.RESUME))
    }

    @Test
    fun pauseIsActiveOnlyWhenAllPausedAndNotExpired() {
        assertTrue(
            isPauseActive(
                allPaused = true,
                pausedUntil = now.plusSeconds(60),
                now = now
            )
        )
        assertFalse(
            isPauseActive(
                allPaused = true,
                pausedUntil = now.minusSeconds(1),
                now = now
            )
        )
        assertFalse(
            isPauseActive(
                allPaused = false,
                pausedUntil = now.plusSeconds(60),
                now = now
            )
        )
        // 无限期暂停
        assertTrue(isPauseActive(allPaused = true, pausedUntil = null, now = now))
    }

    @Test
    fun systemLanguageMapsToEmptyLocaleList() {
        assertNull(localeTagOrNull(LANGUAGE_SYSTEM))
        assertEquals(LANGUAGE_ZH_CN, localeTagOrNull(LANGUAGE_ZH_CN))
        assertEquals(LANGUAGE_ES, localeTagOrNull(LANGUAGE_ES))
        assertEquals(LANGUAGE_PT_BR, localeTagOrNull(LANGUAGE_PT_BR))
    }

    @Test
    fun languageTagsMapToResourceDirectories() {
        assertNull(languageResourceDirectory(LANGUAGE_SYSTEM))
        assertEquals("values", languageResourceDirectory(LANGUAGE_EN))
        assertEquals("values-zh-rCN", languageResourceDirectory(LANGUAGE_ZH_CN))
        assertEquals("values-es", languageResourceDirectory(LANGUAGE_ES))
        assertEquals("values-pt-rBR", languageResourceDirectory(LANGUAGE_PT_BR))
    }
}