package com.example.swingsenseai.analysis

import org.junit.Assert.*
import org.junit.Test

class SwingClassificationKeysTest {

    @Test
    fun `normalize converts to lowercase space-separated`() {
        assertEquals("forehand topspin", SwingClassificationKeys.normalize("Forehand_Topspin"))
        assertEquals("backhand slice", SwingClassificationKeys.normalize("Backhand Slice"))
        assertEquals("idle", SwingClassificationKeys.normalize("  Idle  "))
    }

    @Test
    fun `isIdle detects idle in various formats`() {
        assertTrue(SwingClassificationKeys.isIdle("Idle"))
        assertTrue(SwingClassificationKeys.isIdle("idle"))
        assertTrue(SwingClassificationKeys.isIdle("  IDLE  "))
        assertTrue(SwingClassificationKeys.isIdle("some_idle_state"))
    }

    @Test
    fun `isIdle rejects non-idle`() {
        assertFalse(SwingClassificationKeys.isIdle("Forehand_Topspin"))
        assertFalse(SwingClassificationKeys.isIdle("backhand slice"))
        assertFalse(SwingClassificationKeys.isIdle(""))
    }

    @Test
    fun `isVolleyCategory detects volley`() {
        assertTrue(SwingClassificationKeys.isVolleyCategory("forehand volley"))
        assertTrue(SwingClassificationKeys.isVolleyCategory("backhand volley"))
        assertFalse(SwingClassificationKeys.isVolleyCategory("forehand topspin"))
    }
}
