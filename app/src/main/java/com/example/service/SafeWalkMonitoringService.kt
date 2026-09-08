package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.engine.SafeWalkSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * SafeWalkMonitoringService: Foreground Service keeping safety monitoring active
 * when the user leaves the app, switches applications, locks the phone, or turns off the screen.
 *
 * Acquires a PARTIAL_WAKE_LOCK to prevent CPU sleep during an active walk,
 * and publishes ongoing status notifications with direct emergency cancel actions.
 */
class SafeWalkMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var alertObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()
        observeAlertState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE

        when (action) {
            ACTION_STOP_SERVICE -> {
                stopForegroundService()
            }
            ACTION_CANCEL_ALERT -> {
                val sessionManager = SafeWalkSessionManager.getInstance(applicationContext)
                sessionManager.cancelEmergencyAlert()
                updateNotification(
                    title = "SafeWalk AI — Active Protection",
                    content = "Emergency cancelled. Monitoring continues in background.",
                    isAlert = false
                )
            }
            ACTION_START_SERVICE -> {
                val notification = buildOngoingNotification(
                    title = "SafeWalk AI — Active Protection",
                    content = "Monitoring motion, voice & location in background"
                )

                val sessionManager = SafeWalkSessionManager.getInstance(applicationContext)
                val hasMic = sessionManager.hasMicPermission
                val hasLoc = sessionManager.hasLocationPermission

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    var foregroundType = 0
                    if (hasLoc) {
                        foregroundType = foregroundType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    }
                    if (hasMic) {
                        foregroundType = foregroundType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                    if (foregroundType != 0) {
                        startForeground(NOTIFICATION_ID, notification, foregroundType)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }

        return START_STICKY
    }

    private fun observeAlertState() {
        val sessionManager = SafeWalkSessionManager.getInstance(applicationContext)
        alertObserverJob?.cancel()
        alertObserverJob = serviceScope.launch {
            launch {
                sessionManager.isAlertActive.collectLatest { isAlert ->
                    if (isAlert) {
                        val countdown = sessionManager.alertCountdown.value
                        updateNotification(
                            title = "CRITICAL RISK DETECTED!",
                            content = "Emergency alert in ${countdown}s! Tap if you are safe.",
                            isAlert = true
                        )
                    } else if (sessionManager.alertDispatched.value) {
                        updateNotification(
                            title = "Emergency Alert Dispatched!",
                            content = "Alert sent to emergency contacts with GPS location.",
                            isAlert = true
                        )
                    } else if (sessionManager.isSessionActive.value) {
                        updateNotification(
                            title = "SafeWalk AI — Active Protection",
                            content = "Monitoring motion, voice & location in background",
                            isAlert = false
                        )
                    }
                }
            }

            launch {
                sessionManager.alertCountdown.collectLatest { seconds ->
                    if (sessionManager.isAlertActive.value && seconds > 0) {
                        updateNotification(
                            title = "CRITICAL RISK DETECTED!",
                            content = "Emergency alert in ${seconds}s! Tap if you are safe.",
                            isAlert = true
                        )
                    }
                }
            }

            launch {
                sessionManager.alertDispatched.collectLatest { dispatched ->
                    if (dispatched) {
                        updateNotification(
                            title = "Emergency Alert Dispatched!",
                            content = "Alert sent to emergency contacts with GPS location.",
                            isAlert = true
                        )
                    }
                }
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SafeWalkAI:MonitoringWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // Safety cap 12 hours
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SafeWalk Active Protection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows persistent protection status and critical emergency alerts"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildOngoingNotification(
        title: String,
        content: String,
        isAlert: Boolean = false
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, SafeWalkMonitoringService::class.java).apply {
            action = ACTION_CANCEL_ALERT
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(!isAlert)
            .setAutoCancel(false)
            .setPriority(if (isAlert) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)

        if (isAlert) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "I'M SAFE (CANCEL)",
                cancelPendingIntent
            )
            builder.setVibrate(longArrayOf(0, 300, 200, 300))
        }

        return builder.build()
    }

    private fun updateNotification(title: String, content: String, isAlert: Boolean = false) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildOngoingNotification(title, content, isAlert)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        releaseWakeLock()
        alertObserverJob?.cancel()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        alertObserverJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "safewalk_active_channel"
        const val NOTIFICATION_ID = 40401

        const val ACTION_START_SERVICE = "com.example.safewalk.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.safewalk.action.STOP_SERVICE"
        const val ACTION_CANCEL_ALERT = "com.example.safewalk.action.CANCEL_ALERT"

        fun startService(context: Context) {
            val intent = Intent(context, SafeWalkMonitoringService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SafeWalkMonitoringService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
