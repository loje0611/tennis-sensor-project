package com.example.swingsenseai.inference

import android.util.Log
import com.example.swingsenseai.analysis.EdgeImpulseInputSpec

/**
 * Edge Impulse C++ 분류기 JNI 래퍼.
 * [flatFeatures] 길이는 반드시 [EdgeImpulseInputSpec.FLAT_SIZE]와 같아야 한다.
 *
 * 네이티브 라이브러리 로드 실패 시 앱이 크래시하지 않도록 지연 로드하며,
 * 로드 실패 시 빈 문자열을 반환하여 폴백 모드로 동작한다.
 */
object EdgeImpulseNative {

    private const val TAG = "EdgeImpulseNative"

    @Volatile
    private var nativeLoaded: Boolean = false

    init {
        try {
            System.loadLibrary("swingsense_ei")
            nativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library load failed — classifier disabled", e)
            nativeLoaded = false
        }
    }

    val isAvailable: Boolean get() = nativeLoaded

    fun runClassifier(flatFeatures: FloatArray): String {
        if (!nativeLoaded) return ""

        val expected = EdgeImpulseInputSpec.FLAT_SIZE
        if (flatFeatures.size != expected) {
            Log.w(
                TAG,
                "runClassifier: size mismatch — expected $expected, got ${flatFeatures.size}",
            )
            return ""
        }
        return try {
            runClassifierNative(flatFeatures) ?: ""
        } catch (t: Throwable) {
            Log.e(TAG, "JNI runClassifier crashed", t)
            ""
        }
    }

    @JvmStatic
    private external fun runClassifierNative(flatFeatures: FloatArray): String?
}
