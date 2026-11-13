package com.alex.pilltime.scan

/**
 * 从药盒识别出的文本行里挑选药名候选，纯 JVM 逻辑，便于单元测试。
 * 规则：去空白、丢弃纯数字与纯剂量行、限制长度、忽略大小写去重，
 * 只保留含中文或字母的行，并按原顺序截断。
 */
object MedicineNameExtractor {

    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 40

    private val DOSE_UNIT = Regex("mcg|mg|ml|g|%", RegexOption.IGNORE_CASE)
    private val PURE_NUMBER = Regex("^[0-9\\s.,:;/-]+$")
    private val WHITESPACE = Regex("\\s+")

    fun extract(lines: List<String>, maxCandidates: Int = 5): List<String> {
        if (maxCandidates <= 0) return emptyList()
        val candidates = ArrayList<String>()
        val seen = HashSet<String>()
        for (line in lines) {
            val text = line.trim().replace(WHITESPACE, " ")
            if (text.length < MIN_LENGTH || text.length > MAX_LENGTH) continue
            // 至少要有一个汉字或字母，过滤分隔线、符号行
            if (text.none { it.isLetter() }) continue
            if (PURE_NUMBER.matches(text)) continue
            if (isDoseOnly(text)) continue
            if (!seen.add(text.lowercase())) continue
            candidates.add(text)
            if (candidates.size >= maxCandidates) break
        }
        return candidates
    }

    /**
     * 纯剂量行：含 mg/ml/g/% 之类单位，且去掉单位、数字与常见分隔符后
     * 不再有文字（如 "500 mg"、"10ml"、"5 %"）。
     */
    private fun isDoseOnly(text: String): Boolean {
        if (!DOSE_UNIT.containsMatchIn(text)) return false
        val rest = DOSE_UNIT.replace(text, " ")
            .filterNot { char ->
                char.isDigit() ||
                    char.isWhitespace() ||
                    char in ".,:;/*x×+-~≈()（）"
            }
        return rest.isEmpty()
    }
}