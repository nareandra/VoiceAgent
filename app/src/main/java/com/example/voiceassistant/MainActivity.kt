package com.example.voiceassistant

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.voiceassistant.network.ApiClient
import com.example.voiceassistant.ui.theme.VoiceAgentTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "KALKI_MAIN"
    }

    private lateinit var conversationViewModel: ConversationViewModel

    // Fallback broadcast receiver for wake-word events
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KalkiForegroundService.ACTION_WAKE_WORD_DETECTED) {
                Log.d(TAG, "Wake word broadcast received in MainActivity")
                conversationViewModel.onWakeWordDetected()
            }
        }
    }

    private var isReceiverRegistered = false

    // =========================================================
    // MICROPHONE PERMISSION
    // =========================================================

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "Microphone permission result: $granted")
            if (granted) {
                Log.d(TAG, "Microphone permission granted")
                startKalkiForegroundService()
                requestNotificationPermission()
            } else {
                Log.e(TAG, "Microphone permission denied")
                conversationViewModel.clearError()
            }
        }

    // =========================================================
    // NOTIFICATION PERMISSION
    // =========================================================

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "Notification permission result: $granted")
            startKalkiForegroundService()
        }

    // =========================================================
    // ACTIVITY CREATED
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "======================================")
        Log.d(TAG, "KALKI MainActivity CREATED")
        Log.d(TAG, "======================================")

        // API CLIENT with configurable constants
        val apiClient = ApiClient(
            baseUrl = BuildConfig.BACKEND_BASE_URL,
            apiKey = BuildConfig.BACKEND_API_KEY
        )

        // CONVERSATION VIEW MODEL
        conversationViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ConversationViewModel(application, apiClient) as T
                }
            }
        )[ConversationViewModel::class.java]

        Log.d(TAG, "ConversationViewModel created")

        // COMPOSE UI
        setContent {
            VoiceAgentTheme {
                TapToTalkScreen(viewModel = conversationViewModel)
            }
        }

        // Configure lock-screen display flags so KALKI displays over keyguard when screen is locked
        configureLockScreenDisplay()

        // MICROPHONE PERMISSION CHECK
        checkMicrophonePermission()

        if (intent?.action == KalkiForegroundService.ACTION_WAKE_WORD_DETECTED) {
            conversationViewModel.onWakeWordDetected()
        }
    }

    // =========================================================
    // LOCK SCREEN DISPLAY CONFIGURATION
    // =========================================================

    private fun configureLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // =========================================================
    // CHECK MICROPHONE PERMISSION
    // =========================================================

    private fun checkMicrophonePermission() {
        val permission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        )

        if (permission == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Microphone permission already granted")
            startKalkiForegroundService()
            requestNotificationPermission()
        } else {
            Log.d(TAG, "Requesting microphone permission")
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // =========================================================
    // CHECK NOTIFICATION PERMISSION
    // =========================================================

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )

            if (permission == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission already granted")
                startKalkiForegroundService()
            } else {
                Log.d(TAG, "Requesting notification permission")
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                // Start the service immediately so wake listener is active
                startKalkiForegroundService()
            }
        } else {
            startKalkiForegroundService()
        }
    }

    // =========================================================
    // START KALKI FOREGROUND SERVICE
    // =========================================================

    private fun startKalkiForegroundService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Cannot start service: RECORD_AUDIO not granted yet")
            return
        }

        Log.d(TAG, "Starting KALKI foreground service...")

        try {
            val serviceIntent = Intent(this, KalkiForegroundService::class.java).apply {
                action = KalkiForegroundService.ACTION_START
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.d(TAG, "KALKI foreground service start requested successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start KALKI foreground service", e)
        }
    }

    // =========================================================
    // LIFECYCLE MANAGEMENT
    // =========================================================

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity resumed")
        configureLockScreenDisplay()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startKalkiForegroundService()
        }

        if (!isReceiverRegistered) {
            val filter = IntentFilter(KalkiForegroundService.ACTION_WAKE_WORD_DETECTED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    this,
                    wakeWordReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(wakeWordReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity paused")

        if (isReceiverRegistered) {
            try {
                unregisterReceiver(wakeWordReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureLockScreenDisplay()
        if (intent.action == KalkiForegroundService.ACTION_WAKE_WORD_DETECTED) {
            Log.d(TAG, "onNewIntent: Wake word detected from background")
            conversationViewModel.onWakeWordDetected()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "MainActivity destroyed")
        super.onDestroy()
    }
}