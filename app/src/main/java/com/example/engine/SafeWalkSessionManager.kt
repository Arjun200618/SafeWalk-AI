package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.ai.AiAnalysisResult
import com.example.ai.AiRiskAnalyzer
import com.example.ai.SituationSummary
import com.example.data.ContactPreferences
import com.example.data.EmergencyContact
import com.example.data.MotionStatus
import com.example.data.RiskLevel
import com.example.data.RiskSignal
import com.example.data.SessionHistoryRepository
import com.example.data.SessionRecord
import com.example.service.SafeWalkMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * SafeWalkSessionManager: Central coordinator for safety monitoring.
 *
 * Keeps monitoring alive across foreground UI, background services, lock screens,
 * and app switching without duplicate sensor registrations.
 *
 * Coordinates:
 * - MotionDetector (accelerometer / gyroscope)
 * - LocationTracker (GPS coordinates & stillness)
 * - VoiceTriggerDetector (emergency phrase recognition)
 * - SafetyRiskEngine (fused risk scoring & decay)
 * - 5-second Emergency Countdown & Automatic Multi-Contact Dispatch
 */
class SafeWalkSessionManager private constructor(private val appContext: Context) {

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val contactPreferences = ContactPreferences(appContext)
    val historyRepository = SessionHistoryRepository(appContext)

    var hasMicPermission: Boolean = false
        private set
    var hasLocationPermission: Boolean = false
        private set

    // Risk Engine
    val riskEngine = SafetyRiskEngine(
        scope = managerScope,
        onCriticalAlertTriggered = {
            triggerEmergencyCountdown()
        }
    )

    // AI Risk Analyzer (Secondary intelligence layer)
    val aiRiskAnalyzer = AiRiskAnalyzer(
        context = appContext,
        scope = managerScope
    )
    val aiAnalysisResult: StateFlow<AiAnalysisResult> = aiRiskAnalyzer.analysisResult

    private fun triggerAiAnalysis(
        isSuddenMotion: Boolean = false,
        isPossibleFall: Boolean = false,
        isVoiceEmergency: Boolean = false,
        voicePhrase: String? = null,
        isDemo: Boolean = false,
        simulationType: String? = null
    ) {
        val motion = motionDetector.motionData.value
        val location = locationTracker.locationData.value
        val summary = SituationSummary(
            currentRiskScore = riskEngine.currentScore.value,
            riskLevel = riskEngine.riskLevel.value.name,
            accelMagnitude = motion.accelMagnitude,
            gyroMagnitude = motion.gyroMagnitude,
            suddenMovementDetected = isSuddenMotion || motion.status == MotionStatus.SUDDEN_MOVEMENT,
            possibleFallDetected = isPossibleFall || motion.status == MotionStatus.POSSIBLE_FALL,
            emergencyVoiceDetected = isVoiceEmergency || voiceDetector.lastDetectedPhrase.value != null,
            detectedVoicePhrase = voicePhrase ?: voiceDetector.lastDetectedPhrase.value,
            isMoving = motion.accelMagnitude > 0.4f,
            locationStatus = if (location.hasPermission) "GPS Active" else "GPS Inactive",
            recentEvents = riskEngine.signals.value.map { it.label },
            isDemoSimulation = isDemo,
            simulationType = simulationType
        )
        aiRiskAnalyzer.onSituationUpdated(summary)
    }

    // Motion Detector
    val motionDetector = MotionDetector(
        context = appContext,
        onSuddenMotionDetected = {
            riskEngine.reportSuddenMotion(isDemo = false)
            triggerAiAnalysis(isSuddenMotion = true)
        },
        onPossibleFallDetected = {
            riskEngine.reportPossibleFall(isDemo = false)
            triggerAiAnalysis(isPossibleFall = true)
        }
    )

    // Voice Trigger Detector
    val voiceDetector = VoiceTriggerDetector(
        context = appContext,
        onEmergencyPhraseDetected = { phrase ->
            riskEngine.reportVoiceEmergency(phrase, isDemo = false)
            triggerAiAnalysis(isVoiceEmergency = true, voicePhrase = phrase)
        }
    )

    // Location Tracker
    val locationTracker = LocationTracker(
        context = appContext,
        onProlongedStillnessDetected = {
            riskEngine.reportProlongedStillnessAfterFall(isDemo = false)
            triggerAiAnalysis(isPossibleFall = true)
        }
    )

    // Exposed StateFlows
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    val currentRiskScore: StateFlow<Int> = riskEngine.currentScore
    val currentRiskLevel: StateFlow<RiskLevel> = riskEngine.riskLevel
    val riskSignals: StateFlow<List<RiskSignal>> = riskEngine.signals

    // 5-second countdown state
    private val _isAlertActive = MutableStateFlow(false)
    val isAlertActive: StateFlow<Boolean> = _isAlertActive.asStateFlow()

    private val _alertCountdown = MutableStateFlow(COUNTDOWN_DURATION_SECONDS)
    val alertCountdown: StateFlow<Int> = _alertCountdown.asStateFlow()

    private val _alertDispatched = MutableStateFlow(false)
    val alertDispatched: StateFlow<Boolean> = _alertDispatched.asStateFlow()

    private val _dispatchedContacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val dispatchedContacts: StateFlow<List<EmergencyContact>> = _dispatchedContacts.asStateFlow()

    private val _dispatchedMessage = MutableStateFlow<String?>(null)
    val dispatchedMessage: StateFlow<String?> = _dispatchedMessage.asStateFlow()

    // Internal session stats
    private var sessionStartTime: Long = 0L
    private var cancellationCount: Int = 0
    private var wasAlertTriggeredInSession: Boolean = false

    private var sessionTimerJob: Job? = null
    private var countdownJob: Job? = null

    init {
        // Sync custom phrase with VoiceTriggerDetector
        managerScope.launch {
            contactPreferences.customPhraseFlow.collectLatest { phrase ->
                voiceDetector.updateCustomPhrase(phrase)
            }
        }
    }

    /**
     * Start active SafeWalk monitoring session.
     * Launches the foreground service and registers device sensors.
     */
    fun startSafeWalk(hasMic: Boolean, hasLocation: Boolean) {
        if (_isSessionActive.value) return

        hasMicPermission = hasMic
        hasLocationPermission = hasLocation

        _isSessionActive.value = true
        _sessionDurationSeconds.value = 0L
        _isAlertActive.value = false
        _alertDispatched.value = false
        _alertCountdown.value = COUNTDOWN_DURATION_SECONDS
        _dispatchedContacts.value = emptyList()
        _dispatchedMessage.value = null

        sessionStartTime = System.currentTimeMillis()
        cancellationCount = 0
        wasAlertTriggeredInSession = false

        // Start subsystems
        riskEngine.start()
        motionDetector.startListening()
        voiceDetector.startListening(hasMic)
        locationTracker.startTracking()

        // Start session duration timer
        startSessionTimer()

        // Launch persistent Foreground Service
        SafeWalkMonitoringService.startService(appContext)
    }

    /**
     * Stop active SafeWalk session and save record to history.
     */
    fun stopSafeWalk() {
        if (!_isSessionActive.value && sessionStartTime == 0L) return

        val endTime = System.currentTimeMillis()
        val duration = _sessionDurationSeconds.value
        val peakScore = riskEngine.getPeakScore()

        if (duration > 0L || peakScore > 0) {
            val record = SessionRecord(
                startTime = if (sessionStartTime > 0L) sessionStartTime else (endTime - duration * 1000L),
                endTime = endTime,
                durationSeconds = duration,
                maxRiskScore = peakScore,
                maxRiskLevel = riskEngine.riskLevel.value,
                unusualEventsCount = riskEngine.getUnusualEventsCount(),
                cancellationCount = cancellationCount,
                alertTriggered = wasAlertTriggeredInSession,
                finalLatitude = locationTracker.locationData.value.latitude,
                finalLongitude = locationTracker.locationData.value.longitude
            )
            historyRepository.saveSession(record)
        }

        // Teardown timers
        sessionTimerJob?.cancel()
        sessionTimerJob = null
        countdownJob?.cancel()
        countdownJob = null

        _isSessionActive.value = false
        _isAlertActive.value = false
        _alertDispatched.value = false
        sessionStartTime = 0L

        // Stop subsystems
        riskEngine.stop()
        aiRiskAnalyzer.reset()
        motionDetector.stopListening()
        voiceDetector.stopListening()
        locationTracker.stopTracking()

        // Stop Foreground Service
        SafeWalkMonitoringService.stopService(appContext)
    }

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = managerScope.launch {
            while (isActive) {
                delay(1000L)
                _sessionDurationSeconds.value += 1L
            }
        }
    }

    /**
     * Trigger 5-second emergency countdown.
     */
    fun triggerEmergencyCountdown() {
        if (_isAlertActive.value) return
        wasAlertTriggeredInSession = true
        _isAlertActive.value = true
        _alertDispatched.value = false
        _alertCountdown.value = COUNTDOWN_DURATION_SECONDS

        vibrateUrgentPattern()

        countdownJob?.cancel()
        countdownJob = managerScope.launch {
            while (_alertCountdown.value > 0) {
                delay(1000L)
                _alertCountdown.value -= 1
                vibrateCountdownTick()
            }
            // Countdown finished without user cancellation -> Dispatch emergency alert automatically!
            onCountdownFinished()
        }
    }

    /**
     * User taps "I'M SAFE - CANCEL ALERT" during the 5-second countdown.
     */
    fun cancelEmergencyAlert() {
        countdownJob?.cancel()
        countdownJob = null
        _isAlertActive.value = false
        _alertDispatched.value = false
        _alertCountdown.value = COUNTDOWN_DURATION_SECONDS
        cancellationCount++
        riskEngine.onUserCancelledAlert()
    }

    /**
     * Automatically executed when countdown reaches 0 without user cancellation.
     * Automatically retrieves latest location and sends SMS to all configured emergency contacts.
     */
    private fun onCountdownFinished() {
        _isAlertActive.value = false
        _alertDispatched.value = true

        dispatchEmergencyAlertAutomatically(appContext)
        vibrateAlertDispatched()
    }

    /**
     * Prepares formatted SMS message content with coordinates and Google Maps link.
     */
    fun getEmergencySmsMessage(): String {
        val loc = locationTracker.locationData.value
        return if (loc.latitude != null && loc.longitude != null) {
            val latStr = String.format(Locale.US, "%.5f", loc.latitude)
            val lngStr = String.format(Locale.US, "%.5f", loc.longitude)
            "EMERGENCY ALERT! SafeWalk AI detected a possible danger. Please check on me immediately.\n\nMy current location:\nLatitude: $latStr\nLongitude: $lngStr\n\nGoogle Maps:\nhttps://www.google.com/maps?q=$latStr,$lngStr"
        } else {
            "EMERGENCY ALERT! SafeWalk AI detected a possible danger. Please check on me immediately.\n\nMy current location:\nGPS coordinates acquiring / unavailable.\n\nGoogle Maps:\nhttps://www.google.com/maps"
        }
    }

    /**
     * Automatically dispatches the emergency SMS to every configured contact (1 to 5 contacts).
     */
    fun dispatchEmergencyAlertAutomatically(context: Context): Pair<Int, String> {
        val contacts = contactPreferences.getContacts().filter { it.isConfigured }
        val message = getEmergencySmsMessage()
        _dispatchedMessage.value = message
        _dispatchedContacts.value = contacts

        var sentCount = 0
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSmsPermission && contacts.isNotEmpty()) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val parts = smsManager.divideMessage(message)
                for (contact in contacts) {
                    if (contact.phoneNumber.isNotBlank()) {
                        smsManager.sendMultipartTextMessage(
                            contact.phoneNumber.trim(),
                            null,
                            parts,
                            null,
                            null
                        )
                        sentCount++
                    }
                }
            } catch (_: Exception) {}
        }

        return Pair(sentCount, message)
    }

    /**
     * Fallback or explicit SMS/Share Intent
     */
    fun launchSmsEmergencyIntent(context: Context) {
        val contacts = contactPreferences.getContacts()
        val message = getEmergencySmsMessage()

        val primaryPhone = contacts.firstOrNull { it.isConfigured }?.phoneNumber ?: ""
        val intent = if (primaryPhone.isNotBlank()) {
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$primaryPhone")
                putExtra("sms_body", message)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(Intent.createChooser(shareIntent, "Send SafeWalk AI Alert"))
            } catch (_: Exception) {}
        }
    }

    // ==========================================
    // Simulation / Demo Mode Helpers
    // ==========================================
    fun simulateSuddenMovement() {
        motionDetector.simulateSuddenMovement()
        riskEngine.reportSuddenMotion(isDemo = true)
        triggerAiAnalysis(isSuddenMotion = true, isDemo = true, simulationType = "SUDDEN_MOVEMENT")
    }

    fun simulatePossibleFall() {
        motionDetector.simulatePossibleFall()
        riskEngine.reportPossibleFall(isDemo = true)
        triggerAiAnalysis(isPossibleFall = true, isDemo = true, simulationType = "POSSIBLE_FALL")
    }

    fun simulateVoiceEmergency(phrase: String = "Help me") {
        voiceDetector.simulateVoiceTrigger(phrase, notifyListener = false)
        riskEngine.reportVoiceEmergency(phrase, isDemo = true)
        triggerAiAnalysis(isVoiceEmergency = true, voicePhrase = phrase, isDemo = true, simulationType = "VOICE_EMERGENCY")
    }

    fun simulateCombinedEmergency() {
        motionDetector.simulatePossibleFall()
        riskEngine.reportPossibleFall(isDemo = true)
        val phrase = "Emergency! Save me"
        voiceDetector.simulateVoiceTrigger(phrase, notifyListener = false)
        riskEngine.reportVoiceEmergency(phrase, isDemo = true)
        triggerAiAnalysis(isPossibleFall = true, isVoiceEmergency = true, voicePhrase = phrase, isDemo = true, simulationType = "COMBINED_DANGER")
    }

    fun simulateNormalWalking() {
        motionDetector.simulateNormalWalking()
        riskEngine.reportNormalWalking(isDemo = true)
        triggerAiAnalysis(isDemo = true, simulationType = "NORMAL_WALKING")
    }

    fun resetDemoScore() {
        riskEngine.reset()
        motionDetector.simulateNormalWalking()
        aiRiskAnalyzer.reset()
        _isAlertActive.value = false
        _alertDispatched.value = false
        _alertCountdown.value = COUNTDOWN_DURATION_SECONDS
        countdownJob?.cancel()
        countdownJob = null
    }

    // ==========================================
    // Haptics & Vibration
    // ==========================================
    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibrateUrgentPattern() {
        try {
            val vibrator = getVibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 300, 150, 300, 150, 500),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 300, 150, 300), -1)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateCountdownTick() {
        try {
            val vibrator = getVibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateAlertDispatched() {
        try {
            val vibrator = getVibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(800)
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val COUNTDOWN_DURATION_SECONDS = 5

        @Volatile
        private var INSTANCE: SafeWalkSessionManager? = null

        fun getInstance(context: Context): SafeWalkSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SafeWalkSessionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
