package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.ai.AiAnalysisResult
import com.example.data.EmergencyContact
import com.example.data.LocationData
import com.example.data.MotionData
import com.example.data.RiskLevel
import com.example.data.RiskSignal
import com.example.data.SessionRecord
import com.example.data.VoiceStatus
import com.example.engine.SafeWalkSessionManager
import kotlinx.coroutines.flow.StateFlow

class SafeWalkViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SafeWalkSessionManager.getInstance(application)
    private val contactPrefs = sessionManager.contactPreferences
    private val historyRepo = sessionManager.historyRepository

    // Emergency Contacts (up to 5)
    val contacts: StateFlow<List<EmergencyContact>> = contactPrefs.contactsFlow
    val emergencyContact: StateFlow<EmergencyContact> = contactPrefs.contactFlow
    val customPhrase: StateFlow<String> = contactPrefs.customPhraseFlow
    val sessionHistory: StateFlow<List<SessionRecord>> = historyRepo.sessionsFlow
    val isOnboardingComplete: StateFlow<Boolean> = contactPrefs.onboardingCompleteFlow

    // Session State
    val isSessionActive: StateFlow<Boolean> = sessionManager.isSessionActive
    val sessionDurationSeconds: StateFlow<Long> = sessionManager.sessionDurationSeconds
    val currentRiskScore: StateFlow<Int> = sessionManager.currentRiskScore
    val currentRiskLevel: StateFlow<RiskLevel> = sessionManager.currentRiskLevel
    val riskSignals: StateFlow<List<RiskSignal>> = sessionManager.riskSignals
    val aiAnalysisResult: StateFlow<AiAnalysisResult> = sessionManager.aiAnalysisResult

    val motionData: StateFlow<MotionData> = sessionManager.motionDetector.motionData
    val voiceStatus: StateFlow<VoiceStatus> = sessionManager.voiceDetector.voiceStatus
    val lastDetectedPhrase: StateFlow<String?> = sessionManager.voiceDetector.lastDetectedPhrase
    val locationData: StateFlow<LocationData> = sessionManager.locationTracker.locationData

    // 5-second countdown alert state
    val isAlertActive: StateFlow<Boolean> = sessionManager.isAlertActive
    val alertCountdown: StateFlow<Int> = sessionManager.alertCountdown
    val alertDispatched: StateFlow<Boolean> = sessionManager.alertDispatched
    val dispatchedContacts: StateFlow<List<EmergencyContact>> = sessionManager.dispatchedContacts
    val dispatchedMessage: StateFlow<String?> = sessionManager.dispatchedMessage

    // Contact Management (1 to 5 contacts)
    fun saveContact(contact: EmergencyContact) {
        contactPrefs.saveContact(contact)
    }

    fun addContact(contact: EmergencyContact): Boolean {
        return contactPrefs.addContact(contact)
    }

    fun deleteContact(id: String) {
        contactPrefs.deleteContact(id)
    }

    fun deleteContact() {
        contactPrefs.deleteContact()
    }

    fun saveContacts(contactsList: List<EmergencyContact>) {
        contactPrefs.saveContacts(contactsList)
    }

    fun saveCustomPhrase(phrase: String) {
        contactPrefs.saveCustomEmergencyPhrase(phrase)
    }

    fun completeOnboarding() {
        contactPrefs.setOnboardingComplete(true)
    }

    // Active Session Management
    fun startSafeWalk(hasMicPermission: Boolean = true, hasLocationPermission: Boolean = true) {
        sessionManager.startSafeWalk(hasMicPermission, hasLocationPermission)
    }

    fun stopSafeWalk() {
        sessionManager.stopSafeWalk()
    }

    fun cancelEmergencyAlert() {
        sessionManager.cancelEmergencyAlert()
    }

    fun getEmergencySmsMessage(): String {
        return sessionManager.getEmergencySmsMessage()
    }

    fun launchSmsEmergencyIntent(context: Context) {
        sessionManager.launchSmsEmergencyIntent(context)
    }

    fun dispatchEmergencyAlertAutomatically(context: Context) {
        sessionManager.dispatchEmergencyAlertAutomatically(context)
    }

    // Demo Mode & Testing Simulations
    fun simulateSuddenMovement() {
        sessionManager.simulateSuddenMovement()
    }

    fun simulateSuddenMotion() {
        sessionManager.simulateSuddenMovement()
    }

    fun simulatePossibleFall() {
        sessionManager.simulatePossibleFall()
    }

    fun simulateVoiceEmergency(phrase: String = "Help me") {
        sessionManager.simulateVoiceEmergency(phrase)
    }

    fun triggerDemoVoicePhrase(phrase: String = "Help me") {
        val activePhrase = if (customPhrase.value.isNotBlank()) customPhrase.value else phrase
        sessionManager.simulateVoiceEmergency(activePhrase)
    }

    fun simulateCombinedEmergency() {
        sessionManager.simulateCombinedEmergency()
    }

    fun simulateNormalWalking() {
        sessionManager.simulateNormalWalking()
    }

    fun resetDemoScore() {
        sessionManager.resetDemoScore()
    }

    fun clearSessionHistory() {
        historyRepo.clearHistory()
    }

    override fun onCleared() {
        super.onCleared()
        // Note: Do not force stop SafeWalk here so the foreground service can continue
        // monitoring in the background if the activity or ViewModel is recreated.
    }
}
