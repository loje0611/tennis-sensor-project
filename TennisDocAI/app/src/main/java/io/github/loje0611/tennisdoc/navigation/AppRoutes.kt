package io.github.loje0611.tennisdoc.navigation

object AppRoutes {
    const val LAB = "lab"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ENGINEERING_MODE = "engineering_mode"
    const val SESSION_DETAIL = "session_detail/{sessionId}"

    fun sessionDetail(sessionId: String): String = "session_detail/$sessionId"
}
