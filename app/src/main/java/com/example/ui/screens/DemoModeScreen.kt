package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.PersonalInjury
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RiskLevel
import com.example.data.RiskSignal
import com.example.ui.components.RiskGauge
import com.example.ui.components.RiskSignalCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoModeScreen(
    currentScore: Int,
    riskLevel: RiskLevel,
    riskSignals: List<RiskSignal>,
    onSimulateNormalWalking: () -> Unit,
    onSimulateSuddenMovement: () -> Unit,
    onSimulatePossibleFall: () -> Unit,
    onSimulateEmergencyVoice: () -> Unit,
    onSimulateCombinedEmergency: () -> Unit,
    onResetScore: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hackathon Demo Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onResetScore) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Score"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))

                // Hackathon Badge Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF6366F1).copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Hackathon Demo Mode Active",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF818CF8)
                            )
                            Text(
                                text = "Inject simulated events directly into the real SafetyRiskEngine without staging real emergencies.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real-time Risk Gauge
                RiskGauge(score = currentScore, riskLevel = riskLevel)

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SIMULATE INDIVIDUAL SIGNALS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Normal Walking Simulation
                OutlinedButton(
                    onClick = onSimulateNormalWalking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("demo_normal_walking_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("1. Simulate Normal Walking (Calm)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sudden Movement Spike (+25)
                Button(
                    onClick = onSimulateSuddenMovement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("demo_sudden_motion_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("2. Simulate Sudden Movement Spike (+25)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Possible Fall Impact (+40)
                Button(
                    onClick = onSimulatePossibleFall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("demo_fall_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF97316)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonalInjury,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("3. Simulate Possible Fall (+40)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Emergency Voice Phrase (+60)
                Button(
                    onClick = onSimulateEmergencyVoice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("demo_voice_phrase_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("4. Simulate Emergency Voice Phrase (+60)")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "COMBINED MULTI-SIGNAL TRIGGER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Combined Emergency: Fall + Voice (+100 -> CRITICAL Alert!)
                Button(
                    onClick = onSimulateCombinedEmergency,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("demo_combined_emergency_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simulate Combined Danger (Fall + Voice)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onResetScore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("demo_reset_score_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Risk Score to 0")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Live Feed in Demo Mode
                Text(
                    text = "LIVE SIGNALS RECORDED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (riskSignals.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "Tap any simulation button above to watch signals enter the risk engine in real-time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            items(riskSignals, key = { it.id }) { signal ->
                RiskSignalCard(signal = signal)
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
