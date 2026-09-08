package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.EmergencyContact
import com.example.data.RiskLevel
import com.example.data.RiskSignalType
import com.example.data.SessionRecord
import com.example.data.ContactPreferences
import com.example.data.SessionHistoryRepository
import com.example.engine.SafetyRiskEngine
import com.example.ui.viewmodel.SafeWalkViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `verify app name resource is SafeWalk AI`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SafeWalk AI", appName)
  }

  @Test
  fun `verify emergency contact setup saves and persists correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = ContactPreferences(context)
    val testContact = EmergencyContact(
      name = "Jane Doe",
      phoneNumber = "+15551234567",
      relationship = "Sister"
    )

    prefs.saveContact(testContact)
    val retrieved = prefs.getContact()

    assertTrue(retrieved.isConfigured)
    assertEquals("Jane Doe", retrieved.name)
    assertEquals("+15551234567", retrieved.phoneNumber)
    assertEquals("Sister", retrieved.relationship)

    // Verify custom emergency phrase
    prefs.saveCustomEmergencyPhrase("Code Blue")
    assertEquals("Code Blue", prefs.getCustomEmergencyPhrase())

    // Verify delete
    prefs.deleteContact()
    assertFalse(prefs.getContact().isConfigured)
  }

  @Test
  fun `verify up to 5 emergency contacts management and validation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = ContactPreferences(context)
    prefs.saveContacts(emptyList())

    assertEquals(0, prefs.getContacts().size)

    // Add 5 contacts
    for (i in 1..5) {
      val added = prefs.addContact(
        EmergencyContact(
          name = "Contact $i",
          phoneNumber = "+1555000000$i",
          relationship = "Friend"
        )
      )
      assertTrue(added)
    }

    val savedList = prefs.getContacts()
    assertEquals(5, savedList.size)

    // 6th contact must be rejected (max 5 contacts constraint)
    val sixthAdded = prefs.addContact(
      EmergencyContact(
        name = "Contact 6",
        phoneNumber = "+15550000006"
      )
    )
    assertFalse(sixthAdded)
    assertEquals(5, prefs.getContacts().size)

    // Delete one contact
    val firstId = savedList[0].id
    prefs.deleteContact(firstId)
    assertEquals(4, prefs.getContacts().size)

    // Phone validation checks
    assertTrue(ContactPreferences.isValidPhoneNumber("+1 (555) 123-4567"))
    assertTrue(ContactPreferences.isValidPhoneNumber("1234567890"))
    assertFalse(ContactPreferences.isValidPhoneNumber("abc"))
    assertFalse(ContactPreferences.isValidPhoneNumber("12"))
  }

  @Test
  fun `verify safety risk engine scoring and critical alert triggering`() {
    var criticalAlertFired = false
    val scope = CoroutineScope(Dispatchers.Unconfined)
    val engine = SafetyRiskEngine(
      scope = scope,
      onCriticalAlertTriggered = { criticalAlertFired = true }
    )

    assertEquals(0, engine.currentScore.value)
    assertEquals(RiskLevel.SAFE, engine.riskLevel.value)

    // Test Sudden Motion (+25)
    engine.reportSuddenMotion(isDemo = true)
    assertEquals(25, engine.currentScore.value)
    assertEquals(RiskLevel.SAFE, engine.riskLevel.value)
    assertFalse(criticalAlertFired)

    // Test Possible Fall (+40) -> 25 + 40 = 65 (HIGH_RISK)
    engine.reportPossibleFall(isDemo = true)
    assertEquals(65, engine.currentScore.value)
    assertEquals(RiskLevel.HIGH_RISK, engine.riskLevel.value)
    assertFalse(criticalAlertFired)

    // Test Voice Phrase (+60) -> 65 + 60 = 125 clamped to 100 (CRITICAL)
    engine.reportVoiceEmergency("Help me", isDemo = true)
    assertEquals(100, engine.currentScore.value)
    assertEquals(RiskLevel.CRITICAL, engine.riskLevel.value)
    assertTrue(criticalAlertFired)

    // Test User Cancelled -> resets to SAFE
    engine.onUserCancelledAlert()
    assertEquals(15, engine.currentScore.value)
    assertEquals(RiskLevel.SAFE, engine.riskLevel.value)
  }

  @Test
  fun `verify normal walking simulation decays elevated risk score`() {
    val scope = CoroutineScope(Dispatchers.Unconfined)
    val engine = SafetyRiskEngine(scope = scope, onCriticalAlertTriggered = {})
    engine.reportPossibleFall(isDemo = true) // +40
    assertEquals(40, engine.currentScore.value)
    assertEquals(RiskLevel.CAUTION, engine.riskLevel.value)

    // Simulate normal walking -> -20 decay
    engine.reportNormalWalking(isDemo = true)
    assertEquals(20, engine.currentScore.value)
    assertEquals(RiskLevel.SAFE, engine.riskLevel.value)
  }

  @Test
  fun `verify session history repository saves and clears completed sessions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SessionHistoryRepository(context)
    repo.clearHistory()
    assertTrue(repo.sessionsFlow.value.isEmpty())

    val record = SessionRecord(
      startTime = 1000L,
      endTime = 2000L,
      durationSeconds = 60L,
      maxRiskScore = 75,
      maxRiskLevel = RiskLevel.HIGH_RISK,
      unusualEventsCount = 2,
      alertTriggered = false
    )

    repo.saveSession(record)
    val history = repo.sessionsFlow.value
    assertEquals(1, history.size)
    assertEquals(60L, history[0].durationSeconds)
    assertEquals(75, history[0].maxRiskScore)
    assertEquals(RiskLevel.HIGH_RISK, history[0].maxRiskLevel)

    repo.clearHistory()
    assertTrue(repo.sessionsFlow.value.isEmpty())
  }

  @Test
  fun `verify SafeWalkViewModel full lifecycle and 5-second countdown alert`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = SafeWalkViewModel(application)

    // 1. Initial State
    assertFalse(viewModel.isSessionActive.value)
    assertFalse(viewModel.isAlertActive.value)

    // 2. Start Safe Walk
    viewModel.startSafeWalk(hasMicPermission = false, hasLocationPermission = false)
    assertTrue(viewModel.isSessionActive.value)

    // 3. Test Sudden Motion simulation
    viewModel.simulateSuddenMotion()
    assertEquals(25, viewModel.currentRiskScore.value)
    assertEquals("Sudden Movement Detected", viewModel.motionData.value.status.label)

    // 4. Test Normal Walking simulation
    viewModel.simulateNormalWalking()
    assertEquals(5, viewModel.currentRiskScore.value)
    assertEquals("Normal Walking", viewModel.motionData.value.status.label)

    // 5. Test Combined Danger Simulation (Fall + Voice) -> triggers 5s critical alert overlay
    viewModel.simulateCombinedEmergency()
    assertTrue(viewModel.isAlertActive.value)
    assertEquals(100, viewModel.currentRiskScore.value)
    assertEquals(RiskLevel.CRITICAL, viewModel.currentRiskLevel.value)
    // 5-second countdown requirement
    assertEquals(5, viewModel.alertCountdown.value)

    // Verify SMS message format contains Google Maps URL and coordinates
    val message = viewModel.getEmergencySmsMessage()
    assertTrue(message.contains("EMERGENCY ALERT! SafeWalk AI detected a possible danger."))
    assertTrue(message.contains("Google Maps:"))
    assertTrue(message.contains("https://www.google.com/maps"))

    // 6. Test I'M SAFE - CANCEL ALERT
    viewModel.cancelEmergencyAlert()
    assertFalse(viewModel.isAlertActive.value)
    assertEquals(15, viewModel.currentRiskScore.value)
    assertEquals(RiskLevel.SAFE, viewModel.currentRiskLevel.value)

    // 7. Stop Safe Walk and verify session is saved in history
    viewModel.stopSafeWalk()
    assertFalse(viewModel.isSessionActive.value)
    assertTrue(viewModel.sessionHistory.value.isNotEmpty())
  }

  @Test
  fun `verify AiRiskAnalyzer handles demo mode simulations and non-blocking safety layer`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scope = CoroutineScope(Dispatchers.Unconfined)
    val analyzer = com.example.ai.AiRiskAnalyzer(context, scope, Dispatchers.Unconfined)

    // Initial state
    assertEquals(com.example.ai.AiStatus.READY, analyzer.analysisResult.value.status)
    assertEquals("SAFE", analyzer.analysisResult.value.riskAssessment)

    // 1. Normal Walking Demo Simulation
    analyzer.onSituationUpdated(
      com.example.ai.SituationSummary(
        currentRiskScore = 0,
        riskLevel = "SAFE",
        accelMagnitude = 9.8f,
        gyroMagnitude = 0.1f,
        isDemoSimulation = true,
        simulationType = "NORMAL_WALKING"
      )
    )
    val normalResult = analyzer.analysisResult.value
    assertEquals("SAFE", normalResult.riskAssessment)
    assertTrue(normalResult.confidence >= 90)

    // 2. Sudden Movement Spike Demo Simulation
    analyzer.onSituationUpdated(
      com.example.ai.SituationSummary(
        currentRiskScore = 25,
        riskLevel = "SAFE",
        accelMagnitude = 18.5f,
        gyroMagnitude = 1.2f,
        suddenMovementDetected = true,
        isDemoSimulation = true,
        simulationType = "SUDDEN_MOVEMENT"
      )
    )
    val suddenResult = analyzer.analysisResult.value
    assertEquals("CAUTION", suddenResult.riskAssessment)
    assertTrue(suddenResult.explanation.contains("Sudden acceleration"))

    // 3. Possible Fall Demo Simulation
    analyzer.onSituationUpdated(
      com.example.ai.SituationSummary(
        currentRiskScore = 65,
        riskLevel = "HIGH_RISK",
        accelMagnitude = 1.1f,
        gyroMagnitude = 4.2f,
        possibleFallDetected = true,
        isDemoSimulation = true,
        simulationType = "POSSIBLE_FALL"
      )
    )
    val fallResult = analyzer.analysisResult.value
    assertEquals("HIGH RISK", fallResult.riskAssessment)
    assertTrue(fallResult.explanation.contains("fall") || fallResult.explanation.contains("rotation"))

    // 4. Voice Emergency Demo Simulation
    analyzer.onSituationUpdated(
      com.example.ai.SituationSummary(
        currentRiskScore = 60,
        riskLevel = "HIGH_RISK",
        accelMagnitude = 9.8f,
        gyroMagnitude = 0.2f,
        emergencyVoiceDetected = true,
        detectedVoicePhrase = "Help me",
        isDemoSimulation = true,
        simulationType = "VOICE_EMERGENCY"
      )
    )
    val voiceResult = analyzer.analysisResult.value
    assertEquals("HIGH RISK", voiceResult.riskAssessment)
    assertTrue(voiceResult.explanation.contains("Help me"))

    // 5. Combined Danger Demo Simulation
    analyzer.onSituationUpdated(
      com.example.ai.SituationSummary(
        currentRiskScore = 100,
        riskLevel = "CRITICAL",
        accelMagnitude = 1.0f,
        gyroMagnitude = 4.5f,
        possibleFallDetected = true,
        emergencyVoiceDetected = true,
        detectedVoicePhrase = "Emergency! Save me",
        isDemoSimulation = true,
        simulationType = "COMBINED_DANGER"
      )
    )
    val dangerResult = analyzer.analysisResult.value
    assertEquals("CRITICAL", dangerResult.riskAssessment)
    assertEquals(98, dangerResult.confidence)
  }

  @Test
  fun `verify failsafe behavior when AI is unavailable during live monitoring`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scope = CoroutineScope(Dispatchers.Unconfined)
    val analyzer = com.example.ai.AiRiskAnalyzer(context, scope, Dispatchers.Unconfined)

    // Live monitoring without demo flag when Firebase is unavailable
    analyzer.dispatchAnalysis(
      com.example.ai.SituationSummary(
        currentRiskScore = 30,
        riskLevel = "CAUTION",
        accelMagnitude = 14f,
        gyroMagnitude = 1.2f,
        suddenMovementDetected = true,
        isDemoSimulation = false
      )
    )

    val result = analyzer.analysisResult.value
    assertEquals(com.example.ai.AiStatus.UNAVAILABLE, result.status)
    assertEquals("AI analysis is temporarily unavailable. Safety monitoring remains active.", result.explanation)
  }
}
