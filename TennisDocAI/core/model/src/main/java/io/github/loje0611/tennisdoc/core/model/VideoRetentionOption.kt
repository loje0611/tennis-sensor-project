package io.github.loje0611.tennisdoc.core.model

enum class VideoRetentionOption(val maxCount: Int, val displayName: String, val approximateSize: String) {
    COUNT_20(20, "최근 20개", "약 10 MB"),
    COUNT_50(50, "최근 50개 (권장)", "약 25 MB"),
    COUNT_100(100, "최근 100개", "약 50 MB"),
    COUNT_200(200, "최근 200개", "약 100 MB"),
    UNLIMITED(-1, "무제한", "수동 관리");

    companion object {
        fun fromCount(count: Int): VideoRetentionOption =
            entries.firstOrNull { it.maxCount == count } ?: COUNT_50
    }
}
