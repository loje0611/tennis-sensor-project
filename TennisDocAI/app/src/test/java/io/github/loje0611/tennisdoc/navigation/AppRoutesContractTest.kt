package io.github.loje0611.tennisdoc.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.lang.reflect.Modifier

class AppRoutesContractTest {

    @Test
    fun `lab history and settings routes are stable`() {
        assertEquals("lab", AppRoutes.LAB)
        assertEquals("history", AppRoutes.HISTORY)
        assertEquals("settings", AppRoutes.SETTINGS)
        assertEquals("engineering_mode", AppRoutes.ENGINEERING_MODE)
    }

    @Test
    fun `sessionDetail embeds sessionId`() {
        assertEquals("session_detail/{sessionId}", AppRoutes.SESSION_DETAIL)
        assertEquals("session_detail/abc-123", AppRoutes.sessionDetail("abc-123"))
    }

    @Test
    fun `practice route constant is absent`() {
        val hasPractice = AppRoutes::class.java.declaredFields.any { field ->
            Modifier.isStatic(field.modifiers) &&
                field.name.equals("PRACTICE", ignoreCase = true)
        }
        assertFalse("PRACTICE route must remain removed (D-2)", hasPractice)
    }
}
