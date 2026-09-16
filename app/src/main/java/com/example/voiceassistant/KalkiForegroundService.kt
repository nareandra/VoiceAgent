package com.example.voiceassistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class KalkiForegroundService : Service() {

    companion object {
        private const val TAG = "KALKI_FGS"
        private const val CHANNEL_ID = "kalki_voice_service"
        private const val NOTIFICATION_ID = 1001

        // Service actions
        const val ACTION_START = "com.example.voiceassistant.START_KALKI"
        const val ACTION_STOP = "com.example.voiceassistant.STOP_KALKI"
        const val ACTION_PAUSE_WAKE = "com.example.voiceassistant.PAUSE_WAKE"
        const val ACTION_RESUME_WAKE = "com.example.voiceassistant.RESUME_WAKE"

        // Wake-word broadcast event
        const val ACTION_WAKE_WORD_DETECTED = "com.example.voiceassistant.KALKI_WAKE_WORD_DETECTED"
    }

    private var wakeWordService: WakeWordService? = null

    // =========================================================
    // SERVICE CREATED
    // =========================================================

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "======================================")
        Log.d(TAG, "KALKI Foreground Service CREATED")
        Log.d(TAG, "======================================")

        createNotificationChannel()

        // Wire bridge actions for clean direct coordination
        KalkiBridge.pauseWakeListenerAction = {
            Log.d(TAG, "Bridge hook: pausing wake-word listener")
            wakeWordService?.pause()
        }

        KalkiBridge.resumeWakeListenerAction = {
            Log.d(TAG, "Bridge hook: resuming wake-word listener")
            wakeWordService?.resume()
        }

        // Initialize WakeWordService
        wakeWordService = WakeWordService(
            context = this,
            onWakeWordDetected = {
                onWakeWordDetected()
            }
        )
    }

    // =========================================================
    // SERVICE START COMMAND
    // =========================================================

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() Action = ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stopping KALKI service requested")
                stopKalkiService()
                return START_NOT_STICKY
            }

            ACTION_PAUSE_WAKE -> {
                Log.d(TAG, "Pausing wake listener via Intent action")
                wakeWordService?.pause()
            }

            ACTION_RESUME_WAKE -> {
                Log.d(TAG, "Resuming wake listener via Intent action")
                wakeWordService?.resume()
            }

            ACTION_START, null -> {
                startKalkiService()
            }
        }

        return START_STICKY
    }

    // =========================================================
    // START FOREGROUND SERVICE
    // =========================================================

    private fun startKalkiService() {
        Log.d(TAG, "Starting KALKI foreground service...")

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission is NOT granted. Halting foreground service.")
            stopSelf()
            return
        }

        val notification = createNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.d(TAG, "KALKI FOREGROUND SERVICE ACTIVE with MICROPHONE permission")

            // Start wake-word listener
            wakeWordService?.start()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
        }
    }

    // =========================================================
    // WAKE WORD DETECTED
    // =========================================================

    private fun onWakeWordDetected() {
        Log.d(TAG, "WAKE EVENT: Broadcasting to app components and launching UI")

        val intent = Intent(ACTION_WAKE_WORD_DETECTED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)

        // 1. Direct Activity launch
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            action = ACTION_WAKE_WORD_DETECTED
        }

        try {
            startActivity(openAppIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch activity directly: ${e.message}")
        }

        // 2. Full-Screen Heads-Up Notification (handles Android 10+ background start restrictions)
        try {
            val pendingIntent = PendingIntent.getActivity(
                this,
                1002,
                openAppIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val wakeNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("KALKI is listening…")
                .setContentText("Wake word detected! Go ahead and speak.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, wakeNotification)
        } catch (e: Exception) {
            Log.d(TAG, "Notification trigger error: ${e.message}")
        }
    }

    // =========================================================
    // TASK REMOVED (App swiped away from recent apps)
    // =========================================================

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "App task removed — keeping KALKI background wake-word service alive")
        // Ensure wakeWordService stays listening
        if (wakeWordService == null) {
            wakeWordService = WakeWordService(
                context = this,
                onWakeWordDetected = { onWakeWordDetected() }
            )
        }
        wakeWordService?.start()
    }

    // =========================================================
    // STOP KALKI SERVICE
    // =========================================================

    private fun stopKalkiService() {
        Log.d(TAG, "Stopping KALKI foreground service...")

        KalkiBridge.pauseWakeListenerAction = null
        KalkiBridge.resumeWakeListenerAction = null

        try {
            wakeWordService?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WakeWordService", e)
        }

        wakeWordService = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    // =========================================================
    // NOTIFICATION CHANNEL
    // =========================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KALKI Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "KALKI is active and waiting for the wake word (\"Hey Kalki\")"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    // =========================================================
    // CREATE NOTIFICATION
    // =========================================================

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KALKI AI is active")
            .setContentText("Listening in background — Say \"Hey Kalki\"")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // =========================================================
    // SERVICE DESTROYED
    // =========================================================

    override fun onDestroy() {
        Log.d(TAG, "KALKI Foreground Service DESTROYED")

        KalkiBridge.pauseWakeListenerAction = null
        KalkiBridge.resumeWakeListenerAction = null

        try {
            wakeWordService?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake-word listener", e)
        }

        wakeWordService = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}