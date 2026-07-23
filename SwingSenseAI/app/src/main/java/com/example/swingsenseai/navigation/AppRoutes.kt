package com.example.swingsenseai.navigation

object AppRoutes {
    const val PRACTICE = "practice"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ENGINEERING_MODE = "engineering_mode"
    const val SESSION_DETAIL = "session_detail/{sessionId}"

    fun sessionDetail(sessionId: String): String = "session_detail/$sessionId"
}
