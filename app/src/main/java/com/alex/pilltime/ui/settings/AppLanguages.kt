package com.alex.pilltime.ui.settings

import androidx.annotation.StringRes
import com.alex.pilltime.R

/** 语言选项：BCP 47 标签与显示名资源。 */
data class AppLanguage(
    val tag: String,
    @param:StringRes val labelRes: Int
)

/** 设置页语言列表集中维护，显示名使用各语言的母语写法。 */
val appLanguages: List<AppLanguage> = listOf(
    AppLanguage(LANGUAGE_SYSTEM, R.string.settings_language_system),
    AppLanguage(LANGUAGE_EN, R.string.settings_language_en),
    AppLanguage(LANGUAGE_ZH_CN, R.string.settings_language_zh),
    AppLanguage(LANGUAGE_ES, R.string.settings_language_es),
    AppLanguage(LANGUAGE_PT_BR, R.string.settings_language_pt),
    AppLanguage(LANGUAGE_JA, R.string.settings_language_ja),
    AppLanguage(LANGUAGE_IT, R.string.settings_language_it),
    AppLanguage(LANGUAGE_DE, R.string.settings_language_de),
    AppLanguage(LANGUAGE_ZH_TW, R.string.settings_language_zh_tw),
    AppLanguage(LANGUAGE_ID, R.string.settings_language_id),
    AppLanguage(LANGUAGE_KO, R.string.settings_language_ko),
    AppLanguage(LANGUAGE_AR, R.string.settings_language_ar),
    AppLanguage(LANGUAGE_HI, R.string.settings_language_hi),
    AppLanguage(LANGUAGE_TH, R.string.settings_language_th),
    AppLanguage(LANGUAGE_VI, R.string.settings_language_vi)
)
