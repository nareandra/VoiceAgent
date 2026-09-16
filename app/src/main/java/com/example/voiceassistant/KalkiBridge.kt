package com.example.voiceassistant

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Microphone ownership states to prevent multiple audio recorders
 * from competing for the microphone.
 */
enum class MicOwner {
    WAKE_WORD_MIC,
    CONVERSATION_MIC,
    NO_MIC
}

/**
 * Single source of truth coordinating microphone ownership,
 * state changes, and wake-word events across KalkiForegroundService,
 * WakeWordService, and ConversationViewModel.
 */
object KalkiBridge {

    const val TAG_MIC = "KALKI_MIC"
    const val TAG_STATE = "KALKI_STATE"
    const val TAG_WAKE = "KALKI_WAKE"

    private val _micOwner = MutableStateFlow(MicOwner.NO_MIC)
    val micOwner: StateFlow<MicOwner> = _micOwner.asStateFlow()

    private val _wakeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val wakeEvents: SharedFlow<Unit> = _wakeEvents.asSharedFlow()

    // Action hooks provided by KalkiForegroundService / WakeWordService
    @Volatile
    var pauseWakeListenerAction: (() -> Unit)? = null

    @Volatile
    var resumeWakeListenerAction: (() -> Unit)? = null

    /**
     * Atomically update microphone ownership and log the transition.
     */
    @Synchronized
    fun setMicOwner(newOwner: MicOwner) {
        val previous = _micOwner.value
        if (previous != newOwner) {
            _micOwner.value = newOwner
            Log.d(TAG_MIC, "MIC OWNERSHIP TRANSITION: $previous -> $newOwner")
        }
    }

    /**
     * Log assistant state transitions in standard format.
     */
    fun logStateTransition(from: String, to: String) {
        Log.d(TAG_STATE, "$from -> $to")
    }

    /**
     * Notify subscribers that wake word was detected.
     */
    fun onWakeWordTriggered() {
        Log.d(TAG_WAKE, "Wake event emitted via KalkiBridge")
        _wakeEvents.tryEmit(Unit)
    }

    /**
     * Pause the wake word listener so conversation recording can own the mic.
     */
    fun pauseWakeWordListener() {
        Log.d(TAG_WAKE, "Pausing wake-word listener for conversation mode")
        pauseWakeListenerAction?.invoke()
    }

    /**
     * Resume the wake word listener when conversation mode ends / sleeps.
     */
    fun resumeWakeWordListener() {
        Log.d(TAG_WAKE, "Resuming wake-word listener for sleeping mode")
        resumeWakeListenerAction?.invoke()
    }
}
