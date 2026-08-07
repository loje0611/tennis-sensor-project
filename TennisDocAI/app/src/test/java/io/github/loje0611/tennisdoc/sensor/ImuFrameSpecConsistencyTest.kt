package io.github.loje0611.tennisdoc.sensor

import io.github.loje0611.tennisdoc.core.analysis.EdgeImpulseInputSpec
import io.github.loje0611.tennisdoc.core.sensor.ImuFrameSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class ImuFrameSpecConsistencyTest {

    @Test
    fun `axis count constant matches between core sensor and app input spec`() {
        assertEquals(
            "ImuFrameSpec.AXES_PER_SAMPLE in :core:sensor must match EdgeImpulseInputSpec.AXES_PER_SAMPLE in :app",
            ImuFrameSpec.AXES_PER_SAMPLE,
            EdgeImpulseInputSpec.AXES_PER_SAMPLE,
        )
    }

    @Test
    fun `edge impulse window and flat size remain 40 and 240`() {
        assertEquals(40, EdgeImpulseInputSpec.WINDOW_SAMPLES)
        assertEquals(6, EdgeImpulseInputSpec.AXES_PER_SAMPLE)
        assertEquals(240, EdgeImpulseInputSpec.FLAT_SIZE)
    }
}
