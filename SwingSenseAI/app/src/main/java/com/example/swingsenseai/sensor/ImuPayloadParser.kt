package com.example.swingsenseai.sensor

import com.example.swingsenseai.analysis.EdgeImpulseInputSpec

/**
 * BLE UTF-8 페이로드 "ax,ay,az,gx,gy,gz" 또는 에러 문자열 파싱.
 */
object ImuPayloadParser {

    fun parseLine(line: String): FloatArray? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("ERR:", ignoreCase = true)) return null

        val parts = trimmed.split(',')
        if (parts.size != EdgeImpulseInputSpec.AXES_PER_SAMPLE) return null

        val out = FloatArray(EdgeImpulseInputSpec.AXES_PER_SAMPLE)
        for (i in parts.indices) {
            out[i] = parts[i].trim().toFloatOrNull() ?: return null
        }
        return out
    }
}
