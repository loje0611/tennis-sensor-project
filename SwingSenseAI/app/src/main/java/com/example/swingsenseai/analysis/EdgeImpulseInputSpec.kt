package com.example.swingsenseai.analysis

/**
 * Edge Impulse `model_metadata.h`와 동기화:
 * - [WINDOW_SAMPLES] = `EI_CLASSIFIER_RAW_SAMPLE_COUNT`
 * - [AXES_PER_SAMPLE] = `EI_CLASSIFIER_RAW_SAMPLES_PER_FRAME`
 * - [FLAT_SIZE] = `EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE` (= WINDOW_SAMPLES × AXES_PER_SAMPLE)
 *
 * 50Hz × 40샘플 = 800ms 윈도우 (1200ms/60샘플 구성과 호환되지 않음 — 반드시 헤더와 일치).
 */
object EdgeImpulseInputSpec {
    const val WINDOW_SAMPLES = 40
    const val AXES_PER_SAMPLE = 6
    const val FLAT_SIZE = WINDOW_SAMPLES * AXES_PER_SAMPLE
}
