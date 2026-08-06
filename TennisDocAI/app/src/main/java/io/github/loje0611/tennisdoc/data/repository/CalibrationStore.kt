package io.github.loje0611.tennisdoc.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calibrationDataStore by preferencesDataStore(name = "calibration_settings")

data class CalibrationConfig(
    val volleyAccelThreshold: Float = 2.0f,
    val volleyMaxDurationMs: Int = 350,
    val gyroFollowThroughThreshold: Float = 1200.0f,
    val powerMaxNormalization: Float = 27.0f,
    val spinMaxNormalization: Float = 2100.0f,
    val smoothnessWorstVariance: Float = 50.0f,
) {
    /** g-force → m/s² squared for VolleyDetector internal use. */
    val volleyAccelThresholdSq: Float
        get() {
            val ms2 = volleyAccelThreshold * 9.81f
            return ms2 * ms2
        }

    val gyroFollowThroughThresholdSq: Float
        get() = gyroFollowThroughThreshold * gyroFollowThroughThreshold
}

class CalibrationStore(private val context: Context) {

    private object Keys {
        val VOLLEY_ACCEL_THRESHOLD = floatPreferencesKey("volley_accel_threshold")
        val VOLLEY_MAX_DURATION_MS = intPreferencesKey("volley_max_duration_ms")
        val GYRO_FOLLOW_THROUGH_THRESHOLD = floatPreferencesKey("gyro_follow_through_threshold")
        val POWER_MAX_NORMALIZATION = floatPreferencesKey("power_max_normalization")
        val SPIN_MAX_NORMALIZATION = floatPreferencesKey("spin_max_normalization")
        val SMOOTHNESS_WORST_VARIANCE = floatPreferencesKey("smoothness_worst_variance")
    }

    private val defaults = CalibrationConfig()

    val configFlow: Flow<CalibrationConfig> = context.calibrationDataStore.data.map { prefs ->
        CalibrationConfig(
            volleyAccelThreshold = prefs[Keys.VOLLEY_ACCEL_THRESHOLD]
                ?: defaults.volleyAccelThreshold,
            volleyMaxDurationMs = prefs[Keys.VOLLEY_MAX_DURATION_MS]
                ?: defaults.volleyMaxDurationMs,
            gyroFollowThroughThreshold = prefs[Keys.GYRO_FOLLOW_THROUGH_THRESHOLD]
                ?: defaults.gyroFollowThroughThreshold,
            powerMaxNormalization = prefs[Keys.POWER_MAX_NORMALIZATION]
                ?: defaults.powerMaxNormalization,
            spinMaxNormalization = prefs[Keys.SPIN_MAX_NORMALIZATION]
                ?: defaults.spinMaxNormalization,
            smoothnessWorstVariance = prefs[Keys.SMOOTHNESS_WORST_VARIANCE]
                ?: defaults.smoothnessWorstVariance,
        )
    }

    suspend fun updateVolleyAccelThreshold(value: Float) {
        context.calibrationDataStore.edit { it[Keys.VOLLEY_ACCEL_THRESHOLD] = value }
    }

    suspend fun updateVolleyMaxDurationMs(value: Int) {
        context.calibrationDataStore.edit { it[Keys.VOLLEY_MAX_DURATION_MS] = value }
    }

    suspend fun updateGyroFollowThroughThreshold(value: Float) {
        context.calibrationDataStore.edit { it[Keys.GYRO_FOLLOW_THROUGH_THRESHOLD] = value }
    }

    suspend fun updatePowerMaxNormalization(value: Float) {
        context.calibrationDataStore.edit { it[Keys.POWER_MAX_NORMALIZATION] = value }
    }

    suspend fun updateSpinMaxNormalization(value: Float) {
        context.calibrationDataStore.edit { it[Keys.SPIN_MAX_NORMALIZATION] = value }
    }

    suspend fun updateSmoothnessWorstVariance(value: Float) {
        context.calibrationDataStore.edit { it[Keys.SMOOTHNESS_WORST_VARIANCE] = value }
    }

    suspend fun resetToDefaults() {
        context.calibrationDataStore.edit { it.clear() }
    }
}
