package com.banfromentering.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _warningDistanceMeters =
        MutableStateFlow(prefs.getInt(KEY_WARNING_DISTANCE, DEFAULT_WARNING_DISTANCE_METERS))

    /** Extra distance outside a ban's radius at which the user is warned. */
    val warningDistanceMeters: StateFlow<Int> = _warningDistanceMeters.asStateFlow()

    private val _trackingEnabled = MutableStateFlow(prefs.getBoolean(KEY_TRACKING_ENABLED, false))
    val trackingEnabled: StateFlow<Boolean> = _trackingEnabled.asStateFlow()

    fun setWarningDistanceMeters(meters: Int) {
        prefs.edit { putInt(KEY_WARNING_DISTANCE, meters) }
        _warningDistanceMeters.value = meters
    }

    fun setTrackingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_TRACKING_ENABLED, enabled) }
        _trackingEnabled.value = enabled
    }

    companion object {
        const val DEFAULT_WARNING_DISTANCE_METERS = 500
        private const val KEY_WARNING_DISTANCE = "warning_distance_m"
        private const val KEY_TRACKING_ENABLED = "tracking_enabled"
    }
}
