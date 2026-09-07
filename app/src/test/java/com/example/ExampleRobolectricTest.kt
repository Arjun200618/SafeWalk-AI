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
  fun `verify SafeWalkViewModel full lifecycle and demo simulations`() {
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

    // 5. Test Combined Danger Simulation (Fall + Voice) -> triggers critical alert overlay
    viewModel.simulateCombinedEmergency()
    assertTrue(viewModel.isAlertActive.value)
    assertEquals(100, viewModel.currentRiskScore.value)
    assertEquals(RiskLevel.CRITICAL, viewModel.currentRiskLevel.value)
    assertEquals(10, viewModel.alertCountdown.value)

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
}

