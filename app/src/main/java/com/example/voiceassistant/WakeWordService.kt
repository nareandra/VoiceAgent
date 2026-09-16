package com.example.voiceassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

class WakeWordService(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {

    companion object {
        private const val TAG = "KALKI_WAKE"
        private const val FAST_RESTART_DELAY_MS = 120L
        private const val FULL_RECREATE_DELAY_MS = 500L
        private const val CANONICAL_WAKE_PHRASE = "Hey Kalki"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val appContext: Context = context.applicationContext

    private var speechRecognizer: SpeechRecognizer? = null
    private var enabled = false
    private var isListening = false
    private var wakeDetected = false

    // =========================================================
    // START
    // =========================================================

    fun start() {
        handler.post {
            if (enabled) {
                Log.d(TAG, "Wake listener already running")
                return@post
            }

            if (
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "RECORD_AUDIO permission not granted")
                return@post
            }

            val available = SpeechRecognizer.isRecognitionAvailable(appContext)
            Log.d(TAG, "SpeechRecognizer.isRecognitionAvailable = $available")

            enabled = true
            isListening = false
            wakeDetected = false

            Log.d(TAG, "======================================")
            Log.d(TAG, "KALKI WAKE LISTENER STARTED")
            Log.d(TAG, "Listening for: $CANONICAL_WAKE_PHRASE")
            Log.d(TAG, "======================================")

            KalkiBridge.setMicOwner(MicOwner.WAKE_WORD_MIC)
            KalkiBridge.logStateTransition("INITIALIZING", "SLEEPING")

            createRecognizer()
            startListening()
        }
    }

    // =========================================================
    // PAUSE (Yields microphone to conversation recording)
    // =========================================================

    fun pause() {
        handler.post {
            Log.d(TAG, "Pausing wake-word listener to yield microphone")
            enabled = false
            isListening = false
            wakeDetected = false
            handler.removeCallbacksAndMessages(null)
            destroyRecognizer()
            KalkiBridge.setMicOwner(MicOwner.NO_MIC)
        }
    }

    // =========================================================
    // RESUME (Reclaims microphone for wake-word listening)
    // =========================================================

    fun resume() {
        handler.post {
            Log.d(TAG, "Resuming wake-word listener")
            start()
        }
    }

    // =========================================================
    // STOP
    // =========================================================

    fun stop() {
        handler.post {
            enabled = false
            isListening = false
            wakeDetected = false
            handler.removeCallbacksAndMessages(null)
            destroyRecognizer()
            KalkiBridge.setMicOwner(MicOwner.NO_MIC)
            Log.d(TAG, "Wake listener stopped")
        }
    }

    // =========================================================
    // CREATE RECOGNIZER
    // =========================================================

    private fun createRecognizer() {
        destroyRecognizer()
        Log.d(TAG, "Creating SpeechRecognizer on Service context")

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        Log.d(TAG, "KALKI is actively listening for \"$CANONICAL_WAKE_PHRASE\"")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Audio sound detected by wake listener")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        Log.d(TAG, "Speech ended in current wake window")
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        if (!enabled || wakeDetected) return

                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        val recognizedText = matches?.joinToString(" ")
                            ?.lowercase(Locale.US)?.trim() ?: ""

                        Log.d(TAG, "Recognized: \"$recognizedText\"")

                        if (containsWakeWord(recognizedText)) {
                            wakeWordDetected()
                        } else {
                            fastRestartListening()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!enabled || wakeDetected) return

                        val matches = partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        val recognizedText = matches?.joinToString(" ")
                            ?.lowercase(Locale.US)?.trim() ?: ""

                        if (recognizedText.isBlank()) return

                        Log.d(TAG, "Partial: \"$recognizedText\"")

                        if (containsWakeWord(recognizedText)) {
                            Log.d(TAG, "Wake word matched from PARTIAL results")
                            wakeWordDetected()
                        }
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        if (!enabled || wakeDetected) return

                        val errorName = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                            else -> "UNKNOWN_ERROR_$error"
                        }

                        Log.d(TAG, "Recognition listener event: $error ($errorName)")

                        // Normal silence timeouts: restart immediately without destroying binder
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            fastRestartListening()
                        } else {
                            // Hard or busy errors: recreate after short delay
                            recreateRecognizerSafely()
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate SpeechRecognizer", e)
            recreateRecognizerSafely()
        }
    }

    // =========================================================
    // START LISTENING
    // =========================================================

    private fun startListening() {
        if (!enabled || wakeDetected) return

        val recognizer = speechRecognizer ?: run {
            createRecognizer()
            speechRecognizer
        }

        if (recognizer == null) {
            Log.e(TAG, "SpeechRecognizer is null; scheduling recreate")
            recreateRecognizerSafely()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening error: ${e.message}", e)
            recreateRecognizerSafely()
        }
    }

    // =========================================================
    // FAST RESTART (Keep existing instance, zero blind spot)
    // =========================================================

    private fun fastRestartListening() {
        if (!enabled || wakeDetected) return

        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}

        handler.postDelayed({
            if (enabled && !wakeDetected) {
                startListening()
            }
        }, FAST_RESTART_DELAY_MS)
    }

    // =========================================================
    // RECREATE RECOGNIZER (On client or busy errors)
    // =========================================================

    private fun recreateRecognizerSafely() {
        if (!enabled || wakeDetected) return

        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()

        handler.postDelayed({
            if (enabled && !wakeDetected) {
                createRecognizer()
                startListening()
            }
        }, FULL_RECREATE_DELAY_MS)
    }

    // =========================================================
    // DESTROY RECOGNIZER
    // =========================================================

    private fun destroyRecognizer() {
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
    }

    // =========================================================
    // WAKE WORD CHECK (Comprehensive Phonetic Matching)
    // =========================================================

    private fun containsWakeWord(text: String): Boolean {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return false

        // Comprehensive STT phonetic variations for "Hey Kalki"
        val wakePhrases = listOf(
            "hey kalki",
            "hi kalki",
            "ok kalki",
            "okay kalki",
            "hello kalki",
            "hey calki",
            "hey calky",
            "hey kalky",
            "hey khalki",
            "hey kalke",
            "hey call key",
            "hey call kee",
            "hey callkee",
            "hey colkie",
            "hey colkey",
            "hey calkie",
            "hey cookie",
            "hey khaki",
            "hey calkey",
            "hey kaki",
            "hey koki",
            "kalki wake up",
            "kalki wakeup",
            "kalki wake",
            "calci wake up",
            "calki wake up",
            "kal ki wake up",
            "call key",
            "call kee",
            "kalki",
            "calki",
            "kalke",
            "calci",
            "khalki"
        )

        for (phrase in wakePhrases) {
            if (normalized.contains(phrase)) {
                Log.d(TAG, "======================================")
                Log.d(TAG, "WAKE WORD MATCHED: \"$phrase\" in \"$normalized\"")
                Log.d(TAG, "======================================")
                return true
            }
        }

        // Regex boundary match for kalki / calki / call key / calci
        val regex = Regex("\\b(kalki|calki|kalke|call\\s*key|calci|khalki|kolki|colki)\\b")
        if (regex.containsMatchIn(normalized)) {
            Log.d(TAG, "WAKE WORD REGEX MATCHED in \"$normalized\"")
            return true
        }

        return false
    }

    private fun normalizeText(text: String): String {
        return text.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // =========================================================
    // WAKE DETECTED
    // =========================================================

    private fun wakeWordDetected() {
        if (!enabled || wakeDetected) return

        wakeDetected = true
        enabled = false
        isListening = false
        handler.removeCallbacksAndMessages(null)

        Log.d(TAG, "======================================")
        Log.d(TAG, "KALKI WAKE WORD DETECTED")
        Log.d(TAG, "Switching to CONVERSATION MODE")
        Log.d(TAG, "======================================")

        KalkiBridge.logStateTransition("SLEEPING", "WAKE_DETECTED")
        destroyRecognizer()
        KalkiBridge.setMicOwner(MicOwner.NO_MIC)

        // Give Android 150ms to cleanly release microphone hardware
        handler.postDelayed({
            KalkiBridge.onWakeWordTriggered()
            onWakeWordDetected()
        }, 150L)
    }
}