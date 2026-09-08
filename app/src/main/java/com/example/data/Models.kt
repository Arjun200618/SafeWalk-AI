package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.StatusCaution
import com.example.ui.theme.StatusCritical
import com.example.ui.theme.StatusHighRisk
import com.example.ui.theme.StatusSafe

enum class RiskLevel(val minScore: Int, val maxScore: Int, val label: String) {
    SAFE(0, 29, "SAFE"),
    CAUTION(30, 59, "CAUTION"),
    HIGH_RISK(60, 79, "HIGH RISK"),
    CRITICAL(80, 100, "CRITICAL");

    val color: Color
        get() = when (this) {
            SAFE -> StatusSafe
            CAUTION -> StatusCaution
            HIGH_RISK -> StatusHighRisk
            CRITICAL -> StatusCritical
        }

    companion object {
        fun fromScore(score: Int): RiskLevel {
            val clamped = score.coerceIn(0, 100)
            return when {
                clamped >= 80 -> CRITICAL
                clamped >= 60 -> HIGH_RISK
                clamped >= 30 -> CAUTION
                else -> SAFE
            }
        }
    }
}

enum class MotionStatus(val label: String) {
    NORMAL("Normal Walking"),
    SUDDEN_MOVEMENT("Sudden Movement Detected"),
    POSSIBLE_FALL("Possible Fall Detected")
}

enum class VoiceStatus(val label: String) {
    OFF("Voice Inactive"),
    LISTENING("Actively Listening"),
    TRIGGER_DETECTED("Emergency Phrase Detected!"),
    UNAVAILABLE("Speech Service Unavailable (Demo Trigger Ready)")
}

data class MotionData(
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 9.8f,
    val accelMagnitude: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val gyroMagnitude: Float = 0f,
    val status: MotionStatus = MotionStatus.NORMAL,
    val lastEventTimestamp: Long = 0L
)

data class LocationData(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val hasPermission: Boolean = false,
    val isAvailable: Boolean = false
) {
    fun toDisplayString(): String {
        return if (latitude != null && longitude != null) {
            String.format("%.5f, %.5f", latitude, longitude)
        } else if (!hasPermission) {
            "Permission Needed"
        } else {
            "Acquiring GPS fix..."
        }
    }
}

enum class RiskSignalType(val points: Int, val title: String) {
    NORMAL_RHYTHM(0, "Normal walking cadence"),
    USER_SAFE(0, "User confirmed safe"),
    SUDDEN_MOTION(25, "Sudden motion spike"),
    POSSIBLE_FALL(40, "Possible fall detected"),
    EMERGENCY_VOICE(60, "Emergency phrase detected"),
    REPEATED_MOTION(20, "Repeated unusual motion"),
    PROLONGED_STILLNESS(15, "Prolonged stillness after fall")
}

data class RiskSignal(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: RiskSignalType,
    val label: String,
    val pointsAdded: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)

data class EmergencyContact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val phoneNumber: String = "",
    val relationship: String = ""
) {
    val isConfigured: Boolean
        get() = name.isNotBlank() && phoneNumber.isNotBlank()
}

data class SessionRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val maxRiskScore: Int,
    val maxRiskLevel: RiskLevel,
    val unusualEventsCount: Int,
    val cancellationCount: Int = 0,
    val alertTriggered: Boolean = false,
    val finalLatitude: Double? = null,
    val finalLongitude: Double? = null
)
