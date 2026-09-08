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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PrototypeDisclaimerBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowItWorksScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "How SafeWalk AI Works",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "SYSTEM ARCHITECTURE OVERVIEW",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Multi-Signal Safety Pipeline",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PIPELINE FLOWCHART
            PipelineStepCard(
                step = "1. INGESTION",
                title = "Phone Sensors & Multi-Signal Input",
                description = "Motion + Orientation (Accelerometer & Gyroscope)\n+ Voice Signal (Emergency phrase matching)\n+ Location Context (GPS stillness)",
                icon = Icons.Default.DirectionsWalk,
                tint = Color(0xFF0284C7)
            )

            FlowArrow()

            PipelineStepCard(
                step = "2. FUSION",
                title = "Safety Risk Engine",
                description = "Combines raw signals on-device using threshold weights. Prevents false alarms from isolated sensor anomalies.",
                icon = Icons.Default.Memory,
                tint = Color(0xFF8B5CF6)
            )

            FlowArrow()

            PipelineStepCard(
                step = "3. QUANTIFICATION",
                title = "Dynamic Risk Score (0-100)",
                description = "SAFE (0-29) • CAUTION (30-59)\nHIGH RISK (60-79) • CRITICAL (80-100)\nDecays gradually over calm intervals.",
                icon = Icons.Default.Speed,
                tint = Color(0xFFF59E0B)
            )

            FlowArrow()

            PipelineStepCard(
                step = "4. VERIFICATION",
                title = "User Cancellation Window (5s)",
                description = "If score hits CRITICAL (>=80), a full-screen 5-second countdown opens. User can tap \"I'M SAFE\" to cancel.",
                icon = Icons.Default.CheckCircle,
                tint = Color(0xFF10B981)
            )

            FlowArrow()

            PipelineStepCard(
                step = "5. DISPATCH",
                title = "Emergency Alert Workflow",
                description = "If 5s countdown expires without cancellation, automatically sends emergency alert with GPS location to all saved contacts (up to 5 contacts).",
                icon = Icons.Default.NotificationsActive,
                tint = Color(0xFFEF4444)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // TRANSPARENT SCORING WEIGHTS
            Text(
                text = "SCORING RULES & WEIGHTS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ScoreWeightRow("Sudden motion spike", "+25")
                    ScoreWeightRow("Possible fall impact pattern", "+40")
                    ScoreWeightRow("Emergency voice phrase detected", "+60")
                    ScoreWeightRow("Repeated unusual motion (<10s)", "+20")
                    ScoreWeightRow("Stillness post-fall (>25s)", "+15")
                    ScoreWeightRow("Score decay during calm intervals", "-3 / 3.5s")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // TECHNOLOGY USED SECTION
            Text(
                text = "TECHNOLOGY USED",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            TechCard(
                title = "Accelerometer",
                desc = "Measures 3-axis linear acceleration with high-pass dynamic filtering to spot impacts and rapid velocity changes."
            )

            Spacer(modifier = Modifier.height(8.dp))

            TechCard(
                title = "Gyroscope",
                desc = "Measures rotational velocity (rad/s) to differentiate regular walking from tumbling or phone dropping."
            )

            Spacer(modifier = Modifier.height(8.dp))

            TechCard(
                title = "Speech Recognition (Microphone)",
                desc = "Provides real-time phrase spotting on-device without continuous raw recording or audio storage."
            )

            Spacer(modifier = Modifier.height(8.dp))

            TechCard(
                title = "GPS / Fused Location Provider",
                desc = "Tracks latitude and longitude and verifies whether user remains immobile after an impact."
            )

            Spacer(modifier = Modifier.height(8.dp))

            TechCard(
                title = "Local Risk Scoring Engine",
                desc = "Pure Kotlin on-device state engine coordinating score buildup, decay intervals, and alert states."
            )

            Spacer(modifier = Modifier.height(20.dp))

            PrototypeDisclaimerBanner()

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun PipelineStepCard(
    step: String,
    title: String,
    description: String,
    icon: ImageVector,
    tint: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(tint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = step,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun FlowArrow() {
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ScoreWeightRow(label: String, points: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = points,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (points.startsWith("+")) Color(0xFFEF4444) else Color(0xFF10B981)
        )
    }
}

@Composable
private fun TechCard(title: String, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
