package com.example.engine

import com.example.data.RiskLevel
import com.example.data.RiskSignal
import com.example.data.RiskSignalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SafetyRiskEngine: Combines multi-sensor signals (motion, voice, location context)
 * into a single unified risk score between 0 and 100.
 *
 * Scoring breakdown:
 * - Sudden motion spike: +25
 * - Possible fall detected: +40
 * - Emergency voice phrase: +60
 * - Repeated unusual motion (< 10s): +20
 * - Prolonged stillness post-fall: +15
 *
 * Score decays gradually during calm periods.
 */
class SafetyRiskEngine(
    private val scope: CoroutineScope,
    private val onCriticalAlertTriggered: () -> Unit
) {
    private val _currentScore = MutableStateFlow(0)
    val currentScore: StateFlow<Int> = _currentScore.asStateFlow()

    private val _riskLevel = MutableStateFlow(RiskLevel.SAFE)
    val riskLevel: StateFlow<RiskLevel> = _riskLevel.asStateFlow()

    private val _signals = MutableStateFlow<List<RiskSignal>>(emptyList())
    val signals: StateFlow<List<RiskSignal>> = _signals.asStateFlow()

    private var decayJob: Job? = null
    private var lastMotionTimestamp: Long = 0L
    private var lastFallTimestamp: Long = 0L
    private var unusualEventsCount: Int = 0
    private var peakScoreReached: Int = 0

    fun start() {
        reset()
        startDecayLoop()
    }

    fun stop() {
        decayJob?.cancel()
        decayJob = null
    }

    fun reset() {
        _currentScore.value = 0
        _riskLevel.value = RiskLevel.SAFE
        _signals.value = emptyList()
        lastMotionTimestamp = 0L
        lastFallTimestamp = 0L
        unusualEventsCount = 0
        peakScoreReached = 0
    }

    fun getUnusualEventsCount(): Int = unusualEventsCount
    fun getPeakScore(): Int = peakScoreReached

    /**
     * Records an unusual sudden acceleration or rotation spike (+25).
     * If repeated within 10 seconds, adds a bonus (+20) for repeated erratic motion.
     */
    fun reportSuddenMotion(isDemo: Boolean = false) {
        val now = System.currentTimeMillis()
        unusualEventsCount++

        addSignal(
            RiskSignal(
                type = RiskSignalType.SUDDEN_MOTION,
                label = "Sudden motion spike detected",
                pointsAdded = RiskSignalType.SUDDEN_MOTION.points,
                isDemo = isDemo
            )
        )

        // Check for repeated unusual motion within 10 seconds
        if (lastMotionTimestamp > 0L && (now - lastMotionTimestamp) < 10_000L) {
            addSignal(
                RiskSignal(
                    type = RiskSignalType.REPEATED_MOTION,
                    label = "Repeated unusual motion within 10s",
                    pointsAdded = RiskSignalType.REPEATED_MOTION.points,
                    isDemo = isDemo
                )
            )
        }
        lastMotionTimestamp = now
    }

    /**
     * Records a possible fall pattern (+40).
     */
    fun reportPossibleFall(isDemo: Boolean = false) {
        unusualEventsCount++
        lastFallTimestamp = System.currentTimeMillis()

        addSignal(
            RiskSignal(
                type = RiskSignalType.POSSIBLE_FALL,
                label = "Impact & orientation drop (Possible Fall)",
                pointsAdded = RiskSignalType.POSSIBLE_FALL.points,
                isDemo = isDemo
            )
        )
    }

    /**
     * Records an emergency voice phrase detected (+60).
     */
    fun reportVoiceEmergency(phrase: String, isDemo: Boolean = false) {
        unusualEventsCount++

        addSignal(
            RiskSignal(
                type = RiskSignalType.EMERGENCY_VOICE,
                label = "Emergency phrase triggered: \"$phrase\"",
                pointsAdded = RiskSignalType.EMERGENCY_VOICE.points,
                isDemo = isDemo
            )
        )
    }

    /**
     * Records stationary behavior after a fall (+15).
     */
    fun reportProlongedStillnessAfterFall(isDemo: Boolean = false) {
        val now = System.currentTimeMillis()
        if (lastFallTimestamp > 0L && (now - lastFallTimestamp) in 5_000L..90_000L) {
            addSignal(
                RiskSignal(
                    type = RiskSignalType.PROLONGED_STILLNESS,
                    label = "No movement/location update following fall",
                    pointsAdded = RiskSignalType.PROLONGED_STILLNESS.points,
                    isDemo = isDemo
                )
            )
        }
    }

    /**
     * Records normal rhythmic walking cadence and decays elevated scores.
     */
    fun reportNormalWalking(isDemo: Boolean = false) {
        val current = _currentScore.value
        val decayed = (current - 20).coerceAtLeast(0)
        _currentScore.value = decayed
        _riskLevel.value = RiskLevel.fromScore(decayed)
        addSignal(
            RiskSignal(
                type = RiskSignalType.NORMAL_RHYTHM,
                label = "Normal rhythmic walking cadence (Calm)",
                pointsAdded = 0,
                isDemo = isDemo
            )
        )
    }

    /**
     * User confirmed they are safe, mitigating risk.
     */
    fun onUserCancelledAlert() {
        val safeScore = 15
        _currentScore.value = safeScore
        _riskLevel.value = RiskLevel.SAFE
        _signals.value = listOf(
            RiskSignal(
                type = RiskSignalType.USER_SAFE,
                label = "User marked Safe: Risk score reset",
                pointsAdded = 0
            )
        ) + _signals.value.take(4)
    }

    private fun addSignal(signal: RiskSignal) {
        val newScore = (_currentScore.value + signal.pointsAdded).coerceIn(0, 100)
        _currentScore.value = newScore
        if (newScore > peakScoreReached) {
            peakScoreReached = newScore
        }
        val level = RiskLevel.fromScore(newScore)
        _riskLevel.value = level

        val updatedList = (listOf(signal) + _signals.value).take(15)
        _signals.value = updatedList

        if (level == RiskLevel.CRITICAL) {
            onCriticalAlertTriggered()
        }
    }

    private fun startDecayLoop() {
        decayJob?.cancel()
        decayJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(3500L) // every 3.5 seconds
                if (_currentScore.value > 0 && _riskLevel.value != RiskLevel.CRITICAL) {
                    val decayed = (_currentScore.value - 3).coerceAtLeast(0)
                    _currentScore.value = decayed
                    _riskLevel.value = RiskLevel.fromScore(decayed)
                }
            }
        }
    }
}
