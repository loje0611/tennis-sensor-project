package io.github.loje0611.tennisdoc.core.model

enum class DrillType {
    FOREHAND,
    BACKHAND,
    SERVE,
    FOREHAND_VOLLEY,
    BACKHAND_VOLLEY;

    fun toDisplayName(): String = when (this) {
        FOREHAND -> "포핸드"
        BACKHAND -> "백핸드"
        SERVE -> "서브"
        FOREHAND_VOLLEY -> "포발리"
        BACKHAND_VOLLEY -> "백발리"
    }
}
