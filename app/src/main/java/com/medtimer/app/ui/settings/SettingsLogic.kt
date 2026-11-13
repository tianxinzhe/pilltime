package com.medtimer.app.ui.settings

import java.time.Instant
import java.time.ZoneId

/** 语言标签常量，system 表示跟随系统。 */
const val LANGUAGE_SYSTEM = "system"
const val LANGUAGE_EN = "en"
const val LANGUAGE_ZH_CN = "zh-CN"
const val LANGUAGE_ES = "es"
const val LANGUAGE_PT_BR = "pt-BR"
const val LANGUAGE_JA = "ja"
const val LANGUAGE_IT = "it"
const val LANGUAGE_DE = "de"
const val LANGUAGE_ZH_TW = "zh-TW"
const val LANGUAGE_ID = "id"
const val LANGUAGE_KO = "ko"
const val LANGUAGE_AR = "ar"
const val LANGUAGE_HI = "hi"
const val LANGUAGE_TH = "th"
const val LANGUAGE_VI = "vi"

/** 暂停全部提醒的选项。 */
enum class PauseOption { ONE_HOUR, END_OF_TODAY, RESUME }

/**
 * 计算暂停截止时刻：1 小时按固定 3600 秒；"今天结束"指本地时区次日零点。
 * [PauseOption.RESUME] 返回 null，表示立即恢复。
 */
fun pauseUntil(now: Instant, zone: ZoneId, option: PauseOption): Instant? = when (option) {
    PauseOption.ONE_HOUR -> now.plusSeconds(ONE_HOUR_SECONDS)
    PauseOption.END_OF_TODAY -> now.atZone(zone)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()

    PauseOption.RESUME -> null
}

/** 暂停是否生效：pausedUntil 为空表示无限期暂停。 */
fun isPauseActive(allPaused: Boolean, pausedUntil: Instant?, now: Instant): Boolean =
    allPaused && (pausedUntil == null || pausedUntil.isAfter(now))

/** system 返回 null，对应空 LocaleList。 */
fun localeTagOrNull(language: String): String? =
    if (language == LANGUAGE_SYSTEM) null else language

/** 语言标签到资源目录的映射，供测试与资源自检使用。 */
fun languageResourceDirectory(language: String): String? = when (language) {
    LANGUAGE_SYSTEM -> null
    LANGUAGE_EN -> "values"
    LANGUAGE_ZH_CN -> "values-zh-rCN"
    LANGUAGE_ES -> "values-es"
    LANGUAGE_PT_BR -> "values-pt-rBR"
    LANGUAGE_JA -> "values-ja"
    LANGUAGE_IT -> "values-it"
    LANGUAGE_DE -> "values-de"
    LANGUAGE_ZH_TW -> "values-zh-rTW"
    LANGUAGE_ID -> "values-id"
    LANGUAGE_KO -> "values-ko"
    LANGUAGE_AR -> "values-ar"
    LANGUAGE_HI -> "values-hi"
    LANGUAGE_TH -> "values-th"
    LANGUAGE_VI -> "values-vi"
    else -> null
}

private const val ONE_HOUR_SECONDS = 3_600L