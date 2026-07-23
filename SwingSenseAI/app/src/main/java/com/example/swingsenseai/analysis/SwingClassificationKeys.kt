package com.example.swingsenseai.analysis

import java.util.Locale

/**
 * Edge Impulse `model_variables.h`의 [ei_classifier_inferencing_categories_909575_1]와 동기화.
 * JNI는 C++ `classification[].label` 문자열을 그대로 반환한다.
 */
object SwingClassificationKeys {

    const val BACKHAND_SLICE = "Backhand_Slice"
    const val BACKHAND_TOPSPIN = "Backhand_Topspin"
    const val BACKHAND_VOLLEY = "Backhand_Volley"
    const val FOREHAND_SLICE = "Forehand_Slice"
    const val FOREHAND_TOPSPIN = "Forehand_Topspin"
    const val FOREHAND_VOLLEY = "Forehand_Volley"
    const val IDLE = "Idle"

    /** Room·UI·세션 브레이크다운용 공백 소문자 키 (예: "forehand volley"). */
    fun normalize(raw: String): String =
        raw.trim()
            .replace('_', ' ')
            .lowercase(Locale.US)
            .replace(Regex("\\s+"), " ")

    fun isVolleyCategory(normalizedKey: String): Boolean =
        normalizedKey.contains("volley", ignoreCase = true)

    private val IDLE_REGEX = Regex("\\bidle\\b", RegexOption.IGNORE_CASE)

    /** 모든 레이어에서 Idle 판별에 사용하는 단일 유틸. */
    fun isIdle(raw: String): Boolean {
        val normalized = raw.trim().replace('_', ' ')
        if (normalized.isEmpty()) return false
        return IDLE_REGEX.containsMatchIn(normalized)
    }
}
