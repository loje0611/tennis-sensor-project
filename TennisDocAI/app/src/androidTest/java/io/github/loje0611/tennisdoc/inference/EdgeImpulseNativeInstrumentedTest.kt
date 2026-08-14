package io.github.loje0611.tennisdoc.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.loje0611.tennisdoc.core.analysis.EdgeImpulseInputSpec
import io.github.loje0611.tennisdoc.core.analysis.inference.EdgeImpulseNative
import io.github.loje0611.tennisdoc.core.model.SwingClassificationKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeImpulseNativeInstrumentedTest {

    private val knownLabels = setOf(
        SwingClassificationKeys.BACKHAND_SLICE,
        SwingClassificationKeys.BACKHAND_TOPSPIN,
        SwingClassificationKeys.BACKHAND_VOLLEY,
        SwingClassificationKeys.FOREHAND_SLICE,
        SwingClassificationKeys.FOREHAND_TOPSPIN,
        SwingClassificationKeys.FOREHAND_VOLLEY,
        SwingClassificationKeys.IDLE,
    )

    @Test
    fun nativeLibraryLoadsAndClassifiesOnDevice() {
        assertTrue(
            "libswingsense_ei.so must load on the connected ABI",
            EdgeImpulseNative.isAvailable,
        )

        val zeros = FloatArray(EdgeImpulseInputSpec.FLAT_SIZE)
        val label = EdgeImpulseNative.runClassifier(zeros)
        assertTrue("Classifier must return a non-empty label", label.isNotEmpty())
        assertTrue(
            "Unexpected label '$label'; expected one of $knownLabels",
            label in knownLabels,
        )
    }

    @Test
    fun wrongFeatureSizeReturnsEmptyString() {
        assertTrue(EdgeImpulseNative.isAvailable)
        assertEquals("", EdgeImpulseNative.runClassifier(FloatArray(1)))
        assertEquals("", EdgeImpulseNative.runClassifier(FloatArray(0)))
    }
}
