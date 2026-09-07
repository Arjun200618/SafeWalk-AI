package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ContactPreferences
import com.example.data.EmergencyContact
import com.example.data.LocationData
import com.example.data.MotionData
import com.example.data.RiskLevel
import com.example.data.RiskSignal
import com.example.data.SessionHistoryRepository
import com.example.data.SessionRecord
import com.example.data.VoiceStatus
import com.example.engine.LocationTracker
import com.example.engine.MotionDetector
import com.example.engine.SafetyRiskEngine
import com.example.engine.VoiceTriggerDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SafeWalkViewModel(application: Application) : AndroidViewModel(application) {

    private val contactPrefs = ContactPreferences(application)
    private val historyRepo = SessionHistoryRepository(application)

    val emergencyContact: StateFlow<EmergencyContact> = contactPrefs.contactFlow
    val customPhrase: StateFlow<String> = contactPrefs.customPhraseFlow
    val sessionHistory: StateFlow<List<SessionRecord>> = historyRepo.sessionsFlow
    val isOnboardingComplete: StateFlow<Boolean> = contactPrefs.onboardingCompleteFlow

    // Engines
    private val riskEngine = SafetyRiskEngine(
        scope = viewModelScope,
        onCriticalAlertTriggered = {
            triggerEmergencyCountdown()
        }
    )

    private val motionDetector = MotionDetector(
        context = application,
        onSuddenMotionDetected = {
            riskEngine.reportSuddenMotion(isDemo = false)
        },
        onPossibleFallDetected = {
            riskEngine.reportPossibleFall(isDemo = false)
        }
    )

    private val voiceDetector = VoiceTriggerDetector(
        context = application,
        onEmergencyPhraseDetected = { phrase ->
            riskEngine.reportVoiceEmergency(phrase, isDemo = false)
        }
    )

    private val locationTracker = LocationTracker(
        context = application,
        onProlongedStillnessDetected = {
            riskEngine.reportProlongedStillnessAfterFall(isDemo = false)
        }
    )

    // Exposed State Flows
    val currentRiskScore: StateFlow<Int> = riskEngine.currentScore
    val currentRiskLevel: StateFlow<RiskLevel> = riskEngine.riskLevel
    val riskSignals: StateFlow<List<RiskSignal>> = riskEngine.signals

    val motionData: StateFlow<MotionData> = motionDetector.motionData
    val voiceStatus: StateFlow<VoiceStatus> = voiceDetector.voiceStatus
    val lastDetectedPhrase: StateFlow<String?> = voiceDetector.lastDetectedPhrase
    val locationData: StateFlow<LocationData> = locationTracker.locationData

    // Active session state
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    private var sessionTimerJob: Job? = null
    private var sessionStartTime: Long = 0L

    // Emergency Alert Overlay State
    private val _isAlertActive = MutableStateFlow(false)
    val isAlertActive: StateFlow<Boolean> = _isAlertActive.asStateFlow()

    private val _alertCountdown = MutableStateFlow(10)
    val alertCountdown: StateFlow<Int> = _alertCountdown.asStateFlow()

    private val _alertDispatched = MutableStateFlow(false)
    val alertDispatched: StateFlow<Boolean> = _alertDispatched.asStateFlow()

    private var countdownJob: Job? = null
    private var cancellationCount: Int = 0
    private var wasAlertTriggeredInSession: Boolean = false

    init {
        voiceDetector.updateCustomPhrase(contactPrefs.getCustomEmergencyPhrase())
    }

    fun completeOnboarding() {
        contactPrefs.setOnboardingComplete(true)
    }

    fun saveContact(contact: EmergencyContact) {
        contactPrefs.saveContact(contact)
    }

    fun deleteContact() {
        contactPrefs.deleteContact()
    }

    fun saveCustomPhrase(phrase: String) {
        contactPrefs.saveCustomEmergencyPhrase(phrase)
        voiceDetector.updateCustomPhrase(phrase)
    }

    fun startSafeWalk(hasMicPermission: Boolean, hasLocationPermission: Boolean) {
        _isSessionActive.value = true
        _isAlertActive.value = false
        _alertDispatched.value = false
        _sessionDurationSeconds.value = 0L
        sessionStartTime = System.currentTimeMillis()
        cancellationCount = 0
        wasAlertTriggeredInSession = false

        riskEngine.start()
        motionDetector.startListening()
        voiceDetector.startListening(hasMicPermission)
        if (hasLocationPermission) {
            locationTracker.startTracking()
        }

        startTimer()
    }

    fun stopSafeWalk() {
        val endTime = System.currentTimeMillis()
        val duration = _sessionDurationSeconds.value
        val peakScore = riskEngine.getPeakScore()
        val eventsCount = riskEngine.getUnusualEventsCount()
        val loc = locationTracker.locationData.value

        // Record session to local history
        if (sessionStartTime > 0L) {
            val record = SessionRecord(
                startTime = sessionStartTime,
                endTime = endTime,
                durationSeconds = duration,
                maxRiskScore = peakScore,
                maxRiskLevel = RiskLevel.fromScore(peakScore),
                unusualEventsCount = eventsCount,
                cancellationCount = cancellationCount,
                alertTriggered = wasAlertTriggeredInSession || _alertDispatched.value,
                finalLatitude = loc.latitude,
                finalLongitude = loc.longitude
            )
            historyRepo.saveSession(record)
        }

        // Teardown
        sessionTimerJob?.cancel()
        sessionTimerJob = null
        countdownJob?.cancel()
        countdownJob = null

        _isSessionActive.value = false
        _isAlertActive.value = false
        _alertDispatched.value = false
        sessionStartTime = 0L

        riskEngine.stop()
        motionDetector.stopListening()
        voiceDetector.stopListening()
        locationTracker.stopTracking()
    }

    private fun startTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _sessionDurationSeconds.value += 1L
            }
        }
    }

    /**
     * Trigger full-screen Emergency Alert countdown (10s).
     */
    private fun triggerEmergencyCountdown() {
        if (_isAlertActive.value) return
        wasAlertTriggeredInSession = true
        _isAlertActive.value = true
        _alertDispatched.value = false
        _alertCountdown.value = 10

        vibratePattern()

        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_alertCountdown.value > 0) {
                delay(1000L)
                _alertCountdown.value -= 1
                vibrateTick()
            }
            // Countdown reached zero -> Trigger emergency alert dispatched state!
            _alertDispatched.value = true
            vibrateAlertDispatched()
        }
    }

    /**
     * User taps "I'M SAFE - CANCEL ALERT"
     */
    fun cancelEmergencyAlert() {
        countdownJob?.cancel()
        countdownJob = null
        _isAlertActive.value = false
        _alertDispatched.value = false
        _alertCountdown.value = 10
        cancellationCount++
        riskEngine.onUserCancelledAlert()
    }

    fun triggerDemoVoicePhrase(phrase: String = "Help me") {
        val activePhrase = if (customPhrase.value.isNotBlank()) customPhrase.value else phrase
        voiceDetector.simulateVoiceTrigger(activePhrase, notifyListener = false)
        riskEngine.reportVoiceEmergency(activePhrase, isDemo = true)
    }

    fun simulateSuddenMotion() {
        motionDetector.simulateSuddenMotion()
        riskEngine.reportSuddenMotion(isDemo = true)
    }

    fun simulatePossibleFall() {
        motionDetector.simulatePossibleFall()
        riskEngine.reportPossibleFall(isDemo = true)
    }

    fun simulateVoiceEmergency() {
        val phrase = if (customPhrase.value.isNotBlank()) customPhrase.value else "Help me"
        voiceDetector.simulateVoiceTrigger(phrase, notifyListener = false)
        riskEngine.reportVoiceEmergency(phrase, isDemo = true)
    }

    fun simulateCombinedEmergency() {
        motionDetector.simulatePossibleFall()
        riskEngine.reportPossibleFall(isDemo = true)
        val phrase = "Emergency! Save me"
        voiceDetector.simulateVoiceTrigger(phrase, notifyListener = false)
        riskEngine.reportVoiceEmergency(phrase, isDemo = true)
    }

    fun simulateNormalWalking() {
        motionDetector.simulateNormalWalking()
        riskEngine.reportNormalWalking(isDemo = true)
    }

    fun resetDemoScore() {
        riskEngine.reset()
        motionDetector.simulateNormalWalking()
        _isAlertActive.value = false
        _alertDispatched.value = false
        countdownJob?.cancel()
        countdownJob = null
    }

    fun clearSessionHistory() {
        historyRepo.clearHistory()
    }

    /**
     * Prepares formatted SMS message content as specified in prompt:
     * "SAFEWALK AI ALERT: A possible emergency was detected. Last known location: [latitude, longitude]. Please check on me."
     */
    fun getEmergencySmsMessage(): String {
        val loc = locationTracker.locationData.value
        val locString = if (loc.latitude != null && loc.longitude != null) {
            String.format("%.5f, %.5f (https://maps.google.com/?q=%.5f,%.5f)", loc.latitude, loc.longitude, loc.latitude, loc.longitude)
        } else {
            "Location unavailable / GPS acquiring"
        }
        return "SAFEWALK AI ALERT: A possible emergency was detected. Last known location: $locString. Please check on me."
    }

    fun launchSmsEmergencyIntent(context: Context) {
        val contact = emergencyContact.value
        val message = getEmergencySmsMessage()

        val intent = if (contact.phoneNumber.isNotBlank()) {
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${contact.phoneNumber}")
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
            // Graceful fallback to general share
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

    private fun getVibrator(): Vibrator? {
        val app = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibratePattern() {
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

    private fun vibrateTick() {
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

    override fun onCleared() {
        super.onCleared()
        stopSafeWalk()
    }
}
