package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ActiveSessionScreen
import com.example.ui.screens.DemoModeScreen
import com.example.ui.screens.EmergencyAlertOverlay
import com.example.ui.screens.EmergencyContactsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HowItWorksScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SessionHistoryScreen
import com.example.ui.theme.SafeWalkTheme
import com.example.ui.viewmodel.SafeWalkViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SafeWalkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SafeWalkTheme {
                SafeWalkApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SafeWalkApp(viewModel: SafeWalkViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsState()
    val isSessionActive by viewModel.isSessionActive.collectAsState()
    val isAlertActive by viewModel.isAlertActive.collectAsState()
    val alertCountdown by viewModel.alertCountdown.collectAsState()
    val alertDispatched by viewModel.alertDispatched.collectAsState()

    val riskScore by viewModel.currentRiskScore.collectAsState()
    val riskLevel by viewModel.currentRiskLevel.collectAsState()
    val riskSignals by viewModel.riskSignals.collectAsState()
    val sessionDuration by viewModel.sessionDurationSeconds.collectAsState()

    val motionData by viewModel.motionData.collectAsState()
    val voiceStatus by viewModel.voiceStatus.collectAsState()
    val lastDetectedPhrase by viewModel.lastDetectedPhrase.collectAsState()
    val customPhrase by viewModel.customPhrase.collectAsState()
    val locationData by viewModel.locationData.collectAsState()
    val emergencyContact by viewModel.emergencyContact.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val sessionHistory by viewModel.sessionHistory.collectAsState()
    val aiAnalysisResult by viewModel.aiAnalysisResult.collectAsState()

    // Permissions check helper
    fun checkHasMic(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun checkHasLocation(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun startSafeWalkInternal() {
        if (!isSessionActive) {
            viewModel.startSafeWalk(
                hasMicPermission = checkHasMic(),
                hasLocationPermission = checkHasLocation()
            )
        }
        navController.navigate("active_session")
    }

    // Permission launcher for starting SafeWalk
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Once user finishes permission prompt, start session immediately with granted permissions
        startSafeWalkInternal()
    }

    fun onStartWalkClicked() {
        if (isSessionActive) {
            navController.navigate("active_session")
            return
        }

        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        } else {
            // All already granted: do not ask again!
            startSafeWalkInternal()
        }
    }

    val startDestination = if (isOnboardingComplete) "home" else "onboarding"

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        onGetStarted = {
                            viewModel.completeOnboarding()
                            navController.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    )
                }

                composable("home") {
                    HomeScreen(
                        isSessionActive = isSessionActive,
                        riskLevel = riskLevel,
                        emergencyContact = emergencyContact,
                        contacts = contacts,
                        onStartSafeWalk = { onStartWalkClicked() },
                        onNavigateToContacts = { navController.navigate("contacts") },
                        onNavigateToHistory = { navController.navigate("history") },
                        onNavigateToHowItWorks = { navController.navigate("how_it_works") },
                        onNavigateToDemoMode = { navController.navigate("demo_mode") }
                    )
                }

                composable("active_session") {
                    ActiveSessionScreen(
                        durationSeconds = sessionDuration,
                        riskScore = riskScore,
                        riskLevel = riskLevel,
                        motionData = motionData,
                        voiceStatus = voiceStatus,
                        lastDetectedPhrase = lastDetectedPhrase,
                        customPhrase = customPhrase,
                        locationData = locationData,
                        riskSignals = riskSignals,
                        aiAnalysisResult = aiAnalysisResult,
                        onStopSafeWalk = {
                            viewModel.stopSafeWalk()
                            navController.navigate("home") {
                                popUpTo("active_session") { inclusive = true }
                            }
                        },
                        onTriggerDemoVoice = {
                            viewModel.triggerDemoVoicePhrase()
                        },
                        onNavigateToDemoMode = {
                            navController.navigate("demo_mode")
                        },
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable("contacts") {
                    EmergencyContactsScreen(
                        contacts = contacts,
                        customPhrase = customPhrase,
                        onSaveContact = { viewModel.saveContact(it) },
                        onDeleteContact = { viewModel.deleteContact(it) },
                        onSaveCustomPhrase = { viewModel.saveCustomPhrase(it) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("history") {
                    SessionHistoryScreen(
                        sessions = sessionHistory,
                        onClearHistory = { viewModel.clearSessionHistory() },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("how_it_works") {
                    HowItWorksScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("demo_mode") {
                    DemoModeScreen(
                        currentScore = riskScore,
                        riskLevel = riskLevel,
                        riskSignals = riskSignals,
                        aiAnalysisResult = aiAnalysisResult,
                        onSimulateNormalWalking = { viewModel.simulateNormalWalking() },
                        onSimulateSuddenMovement = { viewModel.simulateSuddenMotion() },
                        onSimulatePossibleFall = { viewModel.simulatePossibleFall() },
                        onSimulateEmergencyVoice = { viewModel.simulateVoiceEmergency() },
                        onSimulateCombinedEmergency = { viewModel.simulateCombinedEmergency() },
                        onResetScore = { viewModel.resetDemoScore() },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // Full-Screen Emergency Alert Countdown Overlay (5 seconds)
        if (isAlertActive) {
            EmergencyAlertOverlay(
                countdownSeconds = alertCountdown,
                isDispatched = alertDispatched,
                emergencyContact = emergencyContact,
                contacts = contacts,
                formattedSmsMessage = viewModel.getEmergencySmsMessage(),
                onCancelAlert = { viewModel.cancelEmergencyAlert() },
                onSendSms = { viewModel.launchSmsEmergencyIntent(context) }
            )
        }
    }
}

// Backward-compatible greeting composable to ensure any test cases compile cleanly
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
