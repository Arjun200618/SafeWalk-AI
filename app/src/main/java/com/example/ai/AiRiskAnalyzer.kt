package com.example.ai

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class AiStatus {
    READY,
    ANALYZING,
    UNAVAILABLE
}

data class SituationSummary(
    val currentRiskScore: Int,
    val riskLevel: String,
    val accelMagnitude: Float,
    val gyroMagnitude: Float,
    val suddenMovementDetected: Boolean = false,
    val possibleFallDetected: Boolean = false,
    val emergencyVoiceDetected: Boolean = false,
    val detectedVoicePhrase: String? = null,
    val isMoving: Boolean = true,
    val locationStatus: String = "GPS Active",
    val recentEvents: List<String> = emptyList(),
    val isDemoSimulation: Boolean = false,
    val simulationType: String? = null
) {
    fun toCompactPrompt(): String {
        return buildString {
            append("Analyze this pedestrian safety telemetry for immediate personal safety risk:\n")
            append("- Current Deterministic Risk Score: $currentRiskScore / 100 ($riskLevel)\n")
            append("- Accelerometer Magnitude: ${String.format(Locale.US, "%.2f", accelMagnitude)} m/s²\n")
            append("- Gyroscope Magnitude: ${String.format(Locale.US, "%.2f", gyroMagnitude)} rad/s\n")
            append("- Sudden Movement Detected: $suddenMovementDetected\n")
            append("- Possible Fall Detected: $possibleFallDetected\n")
            append("- Emergency Voice Trigger: $emergencyVoiceDetected")
            if (!detectedVoicePhrase.isNullOrBlank()) {
                append(" (Phrase: \"$detectedVoicePhrase\")")
            }
            append("\n")
            append("- User Motion State: ${if (isMoving) "In Motion" else "Prolonged Stillness"}\n")
            append("- GPS Status: $locationStatus\n")
            if (recentEvents.isNotEmpty()) {
                append("- Recent Signals: ${recentEvents.takeLast(3).joinToString("; ")}\n")
            }
            append("\nRespond strictly in this 3-line format without markdown formatting:\n")
            append("Risk Assessment: [SAFE | CAUTION | HIGH RISK | CRITICAL]\n")
            append("Confidence: [0-100]%\n")
            append("AI Analysis: [1-2 concise sentences explaining why the telemetry is safe, unusual, or dangerous]")
        }
    }
}

data class AiAnalysisResult(
    val status: AiStatus = AiStatus.READY,
    val riskAssessment: String = "SAFE",
    val confidence: Int = 95,
    val explanation: String = "Monitoring active. All sensor telemetry nominal.",
    val lastAnalyzedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Asynchronous, non-blocking AI risk analysis layer powered by Firebase AI / Gemini.
 * Operates purely as a secondary intelligence layer; does NOT replace, block,
 * or delay the deterministic SafetyRiskEngine.
 */
class AiRiskAnalyzer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val tag = "AiRiskAnalyzer"

    private val _analysisResult = MutableStateFlow(
        AiAnalysisResult(
            status = AiStatus.READY,
            riskAssessment = "SAFE",
            confidence = 95,
            explanation = "Safety monitoring active. Sensor telemetry nominal."
        )
    )
    val analysisResult: StateFlow<AiAnalysisResult> = _analysisResult.asStateFlow()

    private var generativeModel: GenerativeModel? = null
    private var isFirebaseAiConfigured: Boolean? = null

    private var lastAnalysisTimestamp = 0L
    private var lastAnalyzedScore = 0
    private var activeAnalysisJob: Job? = null

    companion object {
        private const val COOLDOWN_MS = 3000L
        private const val SIGNIFICANT_SCORE_DELTA = 20
    }

    init {
        // Probe Firebase AI in the background to avoid blocking initialization
        scope.launch(Dispatchers.IO) {
            checkFirebaseAiAvailability()
        }
    }

    private fun checkFirebaseAiAvailability(): Boolean {
        if (isFirebaseAiConfigured != null) return isFirebaseAiConfigured == true
        return try {
            val app = try {
                FirebaseApp.getInstance()
            } catch (e: Exception) {
                FirebaseApp.initializeApp(context)
            }
            if (app != null) {
                generativeModel = Firebase.ai.generativeModel(modelName = "gemini-2.5-flash")
                isFirebaseAiConfigured = true
                Log.d(tag, "Firebase AI / Gemini initialized successfully")
                true
            } else {
                isFirebaseAiConfigured = false
                false
            }
        } catch (e: Throwable) {
            Log.w(tag, "Firebase AI unavailable or not configured: ${e.message}")
            isFirebaseAiConfigured = false
            false
        }
    }

    /**
     * Non-blocking trigger. Evaluates whether the situation warrants AI analysis
     * (cooldown, significant change, suspicious event, or demo simulation).
     */
    fun onSituationUpdated(summary: SituationSummary) {
        val now = System.currentTimeMillis()

        // Demo events always trigger immediately
        if (summary.isDemoSimulation) {
            dispatchAnalysis(summary)
            return
        }

        // Suspicious triggers
        val hasSuspiciousTrigger = summary.suddenMovementDetected ||
            summary.possibleFallDetected ||
            summary.emergencyVoiceDetected

        // Significant score change
        val hasSignificantScoreChange = kotlin.math.abs(summary.currentRiskScore - lastAnalyzedScore) >= SIGNIFICANT_SCORE_DELTA

        if (!hasSuspiciousTrigger && !hasSignificantScoreChange) {
            return
        }

        // Cooldown enforcement for high frequency sensor updates
        if (now - lastAnalysisTimestamp < COOLDOWN_MS) {
            return
        }

        dispatchAnalysis(summary)
    }

    /**
     * Runs AI analysis asynchronously on Dispatchers.IO.
     * Guaranteed to NEVER block sensor loops or emergency alarms.
     */
    fun dispatchAnalysis(summary: SituationSummary) {
        lastAnalysisTimestamp = System.currentTimeMillis()
        lastAnalyzedScore = summary.currentRiskScore

        // Cancel previous pending analysis if still running
        activeAnalysisJob?.cancel()
        activeAnalysisJob = scope.launch(ioDispatcher) {
            try {
                _analysisResult.value = _analysisResult.value.copy(
                    status = AiStatus.ANALYZING
                )

                val isAvailable = checkFirebaseAiAvailability()
                val model = generativeModel

                if (isAvailable && model != null) {
                    try {
                        val prompt = summary.toCompactPrompt()
                        val response = model.generateContent(prompt)
                        val text = response.text

                        if (!text.isNullOrBlank()) {
                            val parsed = parseGeminiResponse(text, summary.currentRiskScore)
                            _analysisResult.value = parsed
                            return@launch
                        }
                    } catch (modelError: Throwable) {
                        Log.w(tag, "Gemini API call failed: ${modelError.message}")
                    }
                }

                // If real Gemini was unavailable or threw an error:
                if (summary.isDemoSimulation) {
                    // Demo mode requested AI analysis simulation
                    val simulated = generateDemoModeAssessment(summary)
                    _analysisResult.value = simulated
                } else {
                    // Failsafe message requirement:
                    // "AI Status: Unavailable
                    //  AI analysis is temporarily unavailable. Safety monitoring remains active."
                    _analysisResult.value = AiAnalysisResult(
                        status = AiStatus.UNAVAILABLE,
                        riskAssessment = "UNAVAILABLE",
                        confidence = 0,
                        explanation = "AI analysis is temporarily unavailable. Safety monitoring remains active.",
                        lastAnalyzedTimestamp = System.currentTimeMillis()
                    )
                }
            } catch (e: Throwable) {
                Log.e(tag, "Unexpected error in AI analyzer", e)
                _analysisResult.value = AiAnalysisResult(
                    status = AiStatus.UNAVAILABLE,
                    riskAssessment = "UNAVAILABLE",
                    confidence = 0,
                    explanation = "AI analysis is temporarily unavailable. Safety monitoring remains active.",
                    lastAnalyzedTimestamp = System.currentTimeMillis()
                )
            }
        }
    }

    private fun parseGeminiResponse(rawText: String, deterministicScore: Int): AiAnalysisResult {
        var risk = "SAFE"
        var confidence = 85
        var explanation = ""

        val lines = rawText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Risk Assessment:", ignoreCase = true) ||
                trimmed.startsWith("Risk Level:", ignoreCase = true) -> {
                    val raw = trimmed.substringAfter(":").trim().uppercase()
                    risk = when {
                        raw.contains("CRITICAL") -> "CRITICAL"
                        raw.contains("HIGH") -> "HIGH RISK"
                        raw.contains("CAUTION") -> "CAUTION"
                        else -> "SAFE"
                    }
                }
                trimmed.startsWith("Confidence:", ignoreCase = true) -> {
                    val numStr = trimmed.substringAfter(":").replace("%", "").trim()
                    numStr.toIntOrNull()?.let { confidence = it.coerceIn(0, 100) }
                }
                trimmed.startsWith("AI Analysis:", ignoreCase = true) ||
                trimmed.startsWith("Explanation:", ignoreCase = true) -> {
                    explanation = trimmed.substringAfter(":").trim()
                }
            }
        }

        if (explanation.isBlank()) {
            explanation = rawText.trim().take(150)
        }

        // Safety Engine Priority Guarantee:
        // The AI must NOT be allowed to reduce or cancel a CRITICAL emergency detected by the deterministic engine.
        if (deterministicScore >= 80 && (risk == "SAFE" || risk == "CAUTION")) {
            risk = "HIGH RISK"
            explanation = "Deterministic engine detected critical threshold. $explanation"
        }

        return AiAnalysisResult(
            status = AiStatus.READY,
            riskAssessment = risk,
            confidence = confidence,
            explanation = explanation,
            lastAnalyzedTimestamp = System.currentTimeMillis()
        )
    }

    private fun generateDemoModeAssessment(summary: SituationSummary): AiAnalysisResult {
        return when (summary.simulationType) {
            "NORMAL_WALKING" -> AiAnalysisResult(
                status = AiStatus.READY,
                riskAssessment = "SAFE",
                confidence = 96,
                explanation = "Periodic gait patterns and stable sensor variance confirm standard walking motion. No anomaly detected.",
                lastAnalyzedTimestamp = System.currentTimeMillis()
            )
            "SUDDEN_MOVEMENT" -> AiAnalysisResult(
                status = AiStatus.READY,
                riskAssessment = "CAUTION",
                confidence = 84,
                explanation = "Sudden acceleration spike detected without rotational impact. Motion signature suggests an abrupt evasive step or defensive reflex.",
                lastAnalyzedTimestamp = System.currentTimeMillis()
            )
            "POSSIBLE_FALL" -> AiAnalysisResult(
                status = AiStatus.READY,
                riskAssessment = "HIGH RISK",
                confidence = 91,
                explanation = "Abrupt linear deceleration coupled with elevated angular rotation correlates with a rapid drop and fall profile.",
                lastAnalyzedTimestamp = System.currentTimeMillis()
            )
            "VOICE_EMERGENCY" -> AiAnalysisResult(
                status = AiStatus.READY,
                riskAssessment = "HIGH RISK",
                confidence = 93,
                explanation = "Acoustic detection of emergency phrase (\"${summary.detectedVoicePhrase ?: "Help me"}\") recognized. Vocal distress signal significantly elevates risk.",
                lastAnalyzedTimestamp = System.currentTimeMillis()
            )
            "COMBINED_DANGER" -> AiAnalysisResult(
                status = AiStatus.READY,
                riskAssessment = "CRITICAL",
                confidence = 98,
                explanation = "Sudden acceleration combined with unusual gyroscope rotation and an emergency voice phrase indicates a potentially dangerous situation.",
                lastAnalyzedTimestamp = System.currentTimeMillis()
            )
            else -> {
                when {
                    summary.emergencyVoiceDetected && (summary.possibleFallDetected || summary.suddenMovementDetected) -> {
                        AiAnalysisResult(
                            status = AiStatus.READY,
                            riskAssessment = "CRITICAL",
                            confidence = 98,
                            explanation = "Multi-signal fusion: Sudden acceleration combined with unusual gyroscope rotation and an emergency voice phrase indicates a potentially dangerous situation.",
                            lastAnalyzedTimestamp = System.currentTimeMillis()
                        )
                    }
                    summary.possibleFallDetected -> {
                        AiAnalysisResult(
                            status = AiStatus.READY,
                            riskAssessment = "HIGH RISK",
                            confidence = 91,
                            explanation = "Linear deceleration combined with angular rotation velocity indicates a possible fall event.",
                            lastAnalyzedTimestamp = System.currentTimeMillis()
                        )
                    }
                    summary.emergencyVoiceDetected -> {
                        AiAnalysisResult(
                            status = AiStatus.READY,
                            riskAssessment = "HIGH RISK",
                            confidence = 93,
                            explanation = "Distress voice phrase recognized. Voice emergency trigger directly raises situational priority.",
                            lastAnalyzedTimestamp = System.currentTimeMillis()
                        )
                    }
                    summary.suddenMovementDetected -> {
                        AiAnalysisResult(
                            status = AiStatus.READY,
                            riskAssessment = "CAUTION",
                            confidence = 85,
                            explanation = "Abrupt motion variance observed across accelerometer axes. Telemetry suggests sudden evasive action.",
                            lastAnalyzedTimestamp = System.currentTimeMillis()
                        )
                    }
                    summary.currentRiskScore >= 80 -> {
                        AiAnalysisResult(
                            status = AiStatus.READY,
                            riskAssessment = "CRITICAL",
                            confidence = 95,
                            explanation = "Multiple risk signals converged above critical emergency threshold.",
                            lastAnalyzedTimestamp = System.currentTimeMillis()
                        )
                    }
                    else -> {
                        AiAnalysisResult(
                            status = AiStatus.READY,
                            riskAssessment = "SAFE",
                            confidence = 94,
                            explanation = "Telemetry within normal bounds. Safe pedestrian journey in progress.",
                            lastAnalyzedTimestamp = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }

    fun reset() {
        activeAnalysisJob?.cancel()
        _analysisResult.value = AiAnalysisResult(
            status = AiStatus.READY,
            riskAssessment = "SAFE",
            confidence = 95,
            explanation = "Monitoring active. All sensor telemetry nominal.",
            lastAnalyzedTimestamp = System.currentTimeMillis()
        )
    }
}
