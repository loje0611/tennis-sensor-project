package com.example.swingsenseai.ui

import java.util.Locale

/**
 * 모델 라벨(underscore·공백·camelCase)을 화면용 1~2줄로 정리.
 * 예: "Forehand_topspin" → ["Forehand", "Topspin"]
 */
object SwingLabelFormatter {

    fun linesForDisplay(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return listOf("—")

        val normalized = trimmed.replace('_', ' ')
        var parts = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (parts.size == 1) {
            parts = splitCamelCaseToken(parts[0])
        }

        val titled = parts.map { word ->
            word.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString()
            }
        }

        return when (titled.size) {
            0 -> listOf("—")
            1 -> listOf(titled[0])
            else -> listOf(titled[0], titled.drop(1).joinToString(" "))
        }
    }

    /**
     * TTS 전용 문구. UI용 [linesForDisplay]와 별개.
     * - "Topspin"이 단어로 포함되면 → `"Topspin"`만
     * - "Slice"가 단어로 포함되면 → `"Slice"`만
     * - 그 외 → 공백·underscore 기준 **마지막 토큰**만 (표기는 Title Case)
     */
    fun phraseForTtsOnly(raw: String): String {
        if (raw.isBlank()) return ""
        val normalized = raw.trim().replace('_', ' ')
        val lower = normalized.lowercase(Locale.US)

        if (VOLLEY_WORD.containsMatchIn(lower)) {
            return "Volley"
        }
        if (TOPSPIN_WORD.containsMatchIn(lower)) {
            return "Topspin"
        }
        if (SLICE_WORD.containsMatchIn(lower)) {
            return "Slice"
        }

        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val last = tokens.lastOrNull() ?: return ""
        return last.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString()
        }
    }

    private val VOLLEY_WORD = Regex("\\bvolley\\b", RegexOption.IGNORE_CASE)
    private val TOPSPIN_WORD = Regex("\\btopspin\\b", RegexOption.IGNORE_CASE)
    private val SLICE_WORD = Regex("\\bslice\\b", RegexOption.IGNORE_CASE)

    private fun splitCamelCaseToken(token: String): List<String> {
        if (token.length <= 1) return listOf(token)
        val chunks = token.split(Regex("(?<=\\p{javaLowerCase})(?=\\p{javaUpperCase})"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (chunks.size > 1) chunks else listOf(token)
    }
}
