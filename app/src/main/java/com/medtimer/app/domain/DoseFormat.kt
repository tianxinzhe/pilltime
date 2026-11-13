package com.medtimer.app.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * 格式化剂量：qtyX10 是最小单位 x10 的整数，10 → "1"，5 → "0.5"，25 → "2.5"。
 * 用整数除法 + NumberFormat 实现，不经过浮点运算。德国等 locale 会输出 "0,5"。
 */
fun formatDoseQty(qtyX10: Int, locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getInstance(locale)
    format.isGroupingUsed = false
    format.maximumFractionDigits = 1
    format.minimumFractionDigits = 0
    // BigDecimal.valueOf(unscaled, scale) 精确表示 qtyX10 / 10
    return format.format(BigDecimal.valueOf(qtyX10.toLong(), 1))
}

/** 是否恰好 1 个单位，用于 plurals 的 one/other 选择。 */
fun isSingleUnit(qtyX10: Int): Boolean = qtyX10 == 10