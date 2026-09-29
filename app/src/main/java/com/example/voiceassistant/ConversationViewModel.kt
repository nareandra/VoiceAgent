package com.example.voiceassistant

import android.app.Application
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voiceassistant.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

// =========================================================
// ASSISTANT STATE
// =========================================================

enum class AssistantState {
    SLEEPING,
    WAKE_DETECTED,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

// =========================================================
// UI STATE
// =========================================================

data class ConversationUiState(
    val state: AssistantState = AssistantState.SLEEPING,
    val lastTranscript: String = "",
    val lastReply: String = "",
    val errorMessage: String? = null
)

// =========================================================
// CONVERSATION VIEW MODEL
// =========================================================

class ConversationViewModel(
    application: Application,
    private val apiClient: ApiClient
) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "KALKI_CONVERSATION"
        private const val TAG_STATE = "KALKI_STATE"
        private const val TAG_TTS = "KALKI_TTS"
        private const val INACTIVITY_TIMEOUT_MS = 10_000L
        private const val SPEECH_SILENCE_THRESHOLD_MS = 750L
        private const val MIN_SPEECH_DURATION_MS = 300L
        private const val AMPLITUDE_THRESHOLD = 3800
    }

    // UI State
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    // Recording & Playback
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Session & Timers
    private var sessionId: String? = null
    private var inactivityTimerJob: Job? = null
    private var amplitudeMonitorJob: Job? = null

    init {
        // Initialize Platform TextToSpeech as primary fallback
        tts = TextToSpeech(application, this)

        // Listen for wake-word triggers from KalkiBridge
        viewModelScope.launch {
            KalkiBridge.wakeEvents.collect {
                Log.d(TAG, "Received wake event from KalkiBridge")
                onWakeWordDetected()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG_TTS, "US English TTS language is not fully supported on this device")
            } else {
                isTtsReady = true
                Log.d(TAG_TTS, "Platform TextToSpeech initialized successfully")
            }
        } else {
            Log.e(TAG_TTS, "TextToSpeech initialization failed with status: $status")
        }
    }

    // =====================================================
    // WAKE WORD EVENT HANDLER
    // =====================================================

    fun onWakeWordDetected() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "KALKI WAKE WORD DETECTED")
        Log.d(TAG, "========================================")

        // Only transition if not already processing
        if (_uiState.value.state == AssistantState.THINKING) {
            Log.d(TAG, "KALKI is thinking — ignoring wake word")
            return
        }

        // Pause wake-word listener so conversation mode owns the mic
        KalkiBridge.pauseWakeWordListener()

        // Transition: SLEEPING -> WAKE_DETECTED -> LISTENING
        KalkiBridge.logStateTransition(_uiState.value.state.name, AssistantState.WAKE_DETECTED.name)
        _uiState.value = _uiState.value.copy(
            state = AssistantState.WAKE_DETECTED,
            errorMessage = null
        )

        // Play brief acknowledgement chime
        playAcknowledgementChime()

        // Transition to LISTENING for the query
        viewModelScope.launch {
            delay(200L)
            enterListeningMode()
        }
    }

    // =====================================================
    // MANUAL TALK BUTTON PRESSED (Fallback)
    // =====================================================

    fun onTapToTalkPressed() {
        Log.d(TAG, "Manual talk button pressed in state: ${_uiState.value.state}")

        when (_uiState.value.state) {
            AssistantState.SLEEPING,
            AssistantState.ERROR -> {
                KalkiBridge.pauseWakeWordListener()
                enterListeningMode()
            }

            AssistantState.LISTENING -> {
                // User manually stopped talking: send immediately
                cancelInactivityTimer()
                stopRecordingAndSend()
            }

            AssistantState.THINKING,
            AssistantState.SPEAKING -> {
                // Ignore button while thinking or speaking
                Log.d(TAG, "Tap ignored during processing/speaking")
            }

            AssistantState.WAKE_DETECTED -> {
                enterListeningMode()
            }
        }
    }

    fun clearError() {
        stopRecordingSafely()
        KalkiBridge.setMicOwner(MicOwner.NO_MIC)
        _uiState.value = _uiState.value.copy(
            state = AssistantState.SLEEPING,
            errorMessage = null
        )
        KalkiBridge.resumeWakeWordListener()
    }

    // =====================================================
    // QUICK QUERY (Suggestion chips)
    // =====================================================

    fun sendQuickQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        Log.d(TAG, "Quick query triggered: \"$trimmed\"")

        // Stop any active recording or timers
        cancelInactivityTimer()
        stopRecordingSafely()
        stopPlayback()
        KalkiBridge.pauseWakeWordListener()

        // Transition: -> THINKING
        KalkiBridge.logStateTransition(_uiState.value.state.name, AssistantState.THINKING.name)
        _uiState.value = _uiState.value.copy(
            state = AssistantState.THINKING,
            lastTranscript = trimmed,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                Log.d(TAG, "Sending text query to backend: \"$trimmed\"")
                val result = withContext(Dispatchers.IO) {
                    apiClient.converseText(
                        text = trimmed,
                        sessionId = sessionId
                    )
                }

                sessionId = result.sessionId
                Log.d(TAG, "Quick query reply: \"${result.reply}\"")

                val replyText = if (result.reply.isNotBlank()) result.reply else "I am KALKI, your autonomous vehicle assistant."

                // Transition: THINKING -> SPEAKING
                KalkiBridge.logStateTransition(AssistantState.THINKING.name, AssistantState.SPEAKING.name)
                _uiState.value = _uiState.value.copy(
                    state = AssistantState.SPEAKING,
                    lastTranscript = trimmed,
                    lastReply = replyText,
                    errorMessage = null
                )

                // Speak reply
                if (!result.audioBase64.isNullOrBlank()) {
                    playBase64Audio(result.audioBase64, fallbackText = replyText)
                } else {
                    speakWithPlatformTts(replyText)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Quick query failed", e)
                KalkiBridge.logStateTransition(_uiState.value.state.name, AssistantState.ERROR.name)
                _uiState.value = _uiState.value.copy(
                    state = AssistantState.ERROR,
                    errorMessage = "Kalki couldn't connect to the server."
                )
            }
        }
    }

    // =====================================================
    // ENTER LISTENING MODE
    // =====================================================

    private fun enterListeningMode() {
        val previousState = _uiState.value.state.name
        KalkiBridge.logStateTransition(previousState, AssistantState.LISTENING.name)

        _uiState.value = _uiState.value.copy(
            state = AssistantState.LISTENING,
            errorMessage = null
        )

        // Start active recording
        startRecording()

        // Start the 10-second user inactivity countdown
        startInactivityTimer()
    }

    // =====================================================
    // 10-SECOND INACTIVITY TIMER
    // =====================================================

    private fun startInactivityTimer() {
        cancelInactivityTimer()
        inactivityTimerJob = viewModelScope.launch {
            Log.d(TAG, "Inactivity timer started (${INACTIVITY_TIMEOUT_MS / 1000}s)")
            delay(INACTIVITY_TIMEOUT_MS)

            if (_uiState.value.state == AssistantState.LISTENING) {
                Log.d(TAG, "10-second inactivity timer expired without user speech")
                onInactivityTimeout()
            }
        }
    }

    private fun cancelInactivityTimer() {
        inactivityTimerJob?.let {
            if (it.isActive) {
                Log.d(TAG, "Inactivity timer cancelled")
                it.cancel()
            }
        }
        inactivityTimerJob = null
    }

    private fun onInactivityTimeout() {
        Log.d(TAG_STATE, "LISTENING -> SLEEPING (Inactivity timeout)")

        // Stop conversation recording and release microphone
        stopRecordingSafely()
        KalkiBridge.setMicOwner(MicOwner.NO_MIC)

        // Update UI to sleeping state
        _uiState.value = _uiState.value.copy(
            state = AssistantState.SLEEPING,
            errorMessage = null
        )

        // Resume wake word listener
        KalkiBridge.resumeWakeWordListener()
    }

    // =====================================================
    // START CONVERSATION RECORDING
    // =====================================================

    private fun startRecording() {
        stopRecordingSafely()

        val context = getApplication<Application>()
        val outputFile = File(
            context.cacheDir,
            "kalki_utterance_${System.currentTimeMillis()}.m4a"
        )
        recordingFile = outputFile

        try {
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder = newRecorder
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(32000)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            KalkiBridge.setMicOwner(MicOwner.CONVERSATION_MIC)
            Log.d(TAG, "CONVERSATION_MIC acquired; recording started at ${outputFile.name}")

            // Start background VAD monitoring for automatic end-of-speech detection
            startVoiceActivityDetection()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start microphone recording", e)
            stopRecordingSafely()
            KalkiBridge.setMicOwner(MicOwner.NO_MIC)

            _uiState.value = _uiState.value.copy(
                state = AssistantState.ERROR,
                errorMessage = "Microphone error: ${e.message}"
            )
        }
    }

    // =====================================================
    // VOICE ACTIVITY DETECTION (Auto-Detect Speech & Silence)
    // =====================================================

    private fun startVoiceActivityDetection() {
        amplitudeMonitorJob?.cancel()
        amplitudeMonitorJob = viewModelScope.launch(Dispatchers.Default) {
            var speechDurationMs = 0L
            var hasSpoken = false
            var silenceDurationMs = 0L
            val pollIntervalMs = 100L

            while (isActive && recorder != null) {
                delay(pollIntervalMs)

                val maxAmplitude = try {
                    recorder?.maxAmplitude ?: 0
                } catch (_: Exception) {
                    0
                }

                if (maxAmplitude > AMPLITUDE_THRESHOLD) {
                    speechDurationMs += pollIntervalMs
                    if (speechDurationMs >= MIN_SPEECH_DURATION_MS && !hasSpoken) {
                        hasSpoken = true
                        Log.d(TAG, "User speech confirmed on phone ($speechDurationMs ms) -> cancelling 10s inactivity timer")
                        cancelInactivityTimer()
                    }
                    silenceDurationMs = 0L
                } else {
                    speechDurationMs = 0L
                    if (hasSpoken) {
                        silenceDurationMs += pollIntervalMs
                        if (silenceDurationMs >= SPEECH_SILENCE_THRESHOLD_MS) {
                            Log.d(TAG, "Question completed (1.2-second silence reached) -> transitioning to Thinking mode")
                            withContext(Dispatchers.Main) {
                                stopRecordingAndSend()
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    // =====================================================
    // STOP RECORDING & SEND TO BACKEND
    // =====================================================

    private fun stopRecordingAndSend() {
        amplitudeMonitorJob?.cancel()
        amplitudeMonitorJob = null

        val currentRecorder = recorder
        val currentFile = recordingFile

        recorder = null
        recordingFile = null

        if (currentRecorder == null || currentFile == null) {
            Log.w(TAG, "stopRecordingAndSend called with null recorder or file")
            return
        }

        try {
            currentRecorder.stop()
        } catch (_: RuntimeException) {
            // Audio recording was too short
        } finally {
            try { currentRecorder.reset() } catch (_: Exception) {}
            try { currentRecorder.release() } catch (_: Exception) {}
        }

        KalkiBridge.setMicOwner(MicOwner.NO_MIC)

        if (!currentFile.exists() || currentFile.length() < 100L) {
            Log.d(TAG, "Recording was empty or too short. Returning to listening.")
            try { currentFile.delete() } catch (_: Exception) {}
            // Still in conversation mode: resume listening
            if (_uiState.value.state == AssistantState.LISTENING) {
                startRecording()
                startInactivityTimer()
            }
            return
        }

        Log.d(TAG, "Captured utterance: ${currentFile.length()} bytes")

        // Transition: LISTENING -> THINKING
        KalkiBridge.logStateTransition(AssistantState.LISTENING.name, AssistantState.THINKING.name)
        _uiState.value = _uiState.value.copy(
            state = AssistantState.THINKING,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                Log.d(TAG, "Sending audio to KALKI backend...")
                val result = withContext(Dispatchers.IO) {
                    apiClient.converse(
                        audioFile = currentFile,
                        sessionId = sessionId
                    )
                }

                // Preserve session ID for follow-up conversation turns
                sessionId = result.sessionId
                Log.d(TAG, "Backend response received for session: $sessionId")
                Log.d(TAG, "Transcript: \"${result.transcript}\"")
                Log.d(TAG, "Reply: \"${result.reply}\"")

                val cleanTranscript = result.transcript.trim()
                val isNoise = isUnclearAudio(cleanTranscript)

                if (isNoise) {
                    val clarifyText = "I couldn't hear you clearly. Could you please repeat that?"
                    Log.w(TAG, "STT returned unclear/noise audio ($cleanTranscript) -> speaking clarification aloud")

                    // Transition: THINKING -> SPEAKING with clarification
                    KalkiBridge.logStateTransition(AssistantState.THINKING.name, AssistantState.SPEAKING.name)
                    _uiState.value = _uiState.value.copy(
                        state = AssistantState.SPEAKING,
                        lastTranscript = "(Unclear audio)",
                        lastReply = clarifyText,
                        errorMessage = null
                    )

                    // Speak clarification: Fish Audio MP3 if available, otherwise Platform TTS
                    if (!result.audioBase64.isNullOrBlank()) {
                        Log.d(TAG_TTS, "Playing backend audio for clarification")
                        playBase64Audio(result.audioBase64, fallbackText = clarifyText)
                    } else {
                        Log.d(TAG_TTS, "Speaking clarification via platform TTS")
                        speakWithPlatformTts(clarifyText)
                    }
                    return@launch
                }

                // Normal successful transcript
                val replyText = if (result.reply.isNotBlank()) result.reply else "I am here. How can I assist you?"

                // Transition: THINKING -> SPEAKING
                KalkiBridge.logStateTransition(AssistantState.THINKING.name, AssistantState.SPEAKING.name)
                _uiState.value = _uiState.value.copy(
                    state = AssistantState.SPEAKING,
                    lastTranscript = result.transcript,
                    lastReply = replyText,
                    errorMessage = null
                )

                // Speak response: Fish Audio MP3 first, Platform TTS fallback
                if (!result.audioBase64.isNullOrBlank()) {
                    Log.d(TAG_TTS, "Playing Fish Audio response")
                    playBase64Audio(result.audioBase64, fallbackText = replyText)
                } else {
                    Log.d(TAG_TTS, "No backend audio received; speaking with platform TTS")
                    speakWithPlatformTts(replyText)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Backend conversation request failed", e)
                KalkiBridge.logStateTransition(_uiState.value.state.name, AssistantState.ERROR.name)
                _uiState.value = _uiState.value.copy(
                    state = AssistantState.ERROR,
                    errorMessage = "Kalki couldn't connect to the server."
                )
            } finally {
                try {
                    if (currentFile.exists()) currentFile.delete()
                } catch (_: Exception) {}
            }
        }
    }

    private fun isUnclearAudio(text: String): Boolean {
        val trimmed = text.trim().lowercase(Locale.US)
        if (trimmed.isEmpty()) return true
        val noiseTokens = listOf(
            "<noise>", "<silence>", "[noise]", "[silence]",
            "noise", "silence", "...", ".", "00:00", "0:00"
        )
        if (noiseTokens.contains(trimmed)) return true
        if (trimmed.contains("<noise>") || trimmed.contains("[noise]") || trimmed.contains("<silence>")) {
            val stripped = trimmed.replace(Regex("<[^>]+>|\\[[^\\]]+\\]|[0-9:.\\s\\->]+"), "")
            if (stripped.isEmpty()) return true
        }
        if (trimmed.matches(Regex("^\\[?[0-9:.]+(\\s*-->?\\s*[0-9:.]+)?\\]?\\s*(<noise>|\\[noise\\]|noise)?$"))) {
            return true
        }
        return false
    }

    // =====================================================
    // TTS: BASE64 AUDIO PLAYBACK
    // =====================================================

    private fun playBase64Audio(base64Audio: String, fallbackText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                if (audioBytes.isEmpty()) throw IOException("Empty Base64 audio")

                val audioFile = File(
                    getApplication<Application>().cacheDir,
                    "kalki_reply_${System.currentTimeMillis()}.mp3"
                )
                audioFile.writeBytes(audioBytes)

                withContext(Dispatchers.Main) {
                    playAudioFile(audioFile, fallbackText)
                }

            } catch (e: Exception) {
                Log.w(TAG_TTS, "Base64 decode/save failed (${e.message}). Falling back to platform TTS.")
                withContext(Dispatchers.Main) {
                    speakWithPlatformTts(fallbackText)
                }
            }
        }
    }

    private fun playAudioFile(audioFile: File, fallbackText: String) {
        stopPlayback()

        try {
            val player = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnPreparedListener {
                    Log.d(TAG_TTS, "MediaPlayer started playback")
                    it.start()
                }
                setOnCompletionListener {
                    Log.d(TAG_TTS, "MediaPlayer completed playback")
                    it.release()
                    mediaPlayer = null
                    try { audioFile.delete() } catch (_: Exception) {}
                    onSpeakingCompleted()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG_TTS, "MediaPlayer error ($what, $extra); falling back to platform TTS")
                    mp.release()
                    mediaPlayer = null
                    try { audioFile.delete() } catch (_: Exception) {}
                    speakWithPlatformTts(fallbackText)
                    true
                }
            }
            mediaPlayer = player
            player.prepareAsync()

        } catch (e: Exception) {
            Log.e(TAG_TTS, "Failed to start MediaPlayer playback", e)
            speakWithPlatformTts(fallbackText)
        }
    }

    // =====================================================
    // TTS: PLATFORM TTS FALLBACK
    // =====================================================

    private fun speakWithPlatformTts(text: String) {
        if (!isTtsReady || tts == null) {
            Log.w(TAG_TTS, "Platform TTS not initialized yet. Skipping speech.")
            onSpeakingCompleted()
            return
        }

        val utteranceId = "kalki_reply_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG_TTS, "Platform TTS started speaking")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG_TTS, "Platform TTS finished speaking")
                viewModelScope.launch(Dispatchers.Main) {
                    onSpeakingCompleted()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG_TTS, "Platform TTS error on utterance: $utteranceId")
                viewModelScope.launch(Dispatchers.Main) {
                    onSpeakingCompleted()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG_TTS, "Platform TTS error $errorCode on utterance: $utteranceId")
                viewModelScope.launch(Dispatchers.Main) {
                    onSpeakingCompleted()
                }
            }
        })

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG_TTS, "tts.speak returned ERROR")
            onSpeakingCompleted()
        }
    }

    // =====================================================
    // SPEAKING COMPLETED: CONTINUOUS CONVERSATION MODE
    // =====================================================

    private fun onSpeakingCompleted() {
        viewModelScope.launch(Dispatchers.Main) {
            delay(400L)
            if (_uiState.value.state != AssistantState.SPEAKING) return@launch

            // KALKI remains awake! Continuous conversation loop.
            Log.d(TAG_STATE, "SPEAKING -> LISTENING (Continuing conversation)")

            _uiState.value = _uiState.value.copy(
                state = AssistantState.LISTENING,
                errorMessage = null
            )

            // Immediately start listening for follow-up question
            startRecording()

            // Start the 10-second inactivity countdown
            startInactivityTimer()
        }
    }

    // =====================================================
    // AUDIO FEEDBACK & CLEANUP
    // =====================================================

    private fun playAcknowledgementChime() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            viewModelScope.launch {
                delay(200L)
                toneGen.release()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Acknowledgement tone error: ${e.message}")
        }
    }

    private fun stopPlayback() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun stopRecordingSafely() {
        amplitudeMonitorJob?.cancel()
        amplitudeMonitorJob = null

        val currentRecorder = recorder
        recorder = null

        if (currentRecorder != null) {
            try { currentRecorder.stop() } catch (_: Exception) {}
            try { currentRecorder.reset() } catch (_: Exception) {}
            try { currentRecorder.release() } catch (_: Exception) {}
        }

        recordingFile?.let {
            try {
                if (it.exists()) it.delete()
            } catch (_: Exception) {}
        }
        recordingFile = null
    }

    override fun onCleared() {
        cancelInactivityTimer()
        stopRecordingSafely()
        stopPlayback()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null

        super.onCleared()
    }
}