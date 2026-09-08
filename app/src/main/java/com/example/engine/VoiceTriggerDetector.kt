package com.example.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.data.VoiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * VoiceTriggerDetector: Listens on-device for safety emergency phrases:
 * "Help me", "Emergency", "Save me", or a user-defined custom emergency phrase.
 *
 * Designed with a stable lifecycle that avoids rapid reconnect cycles or busy-looping.
 * No raw audio data is recorded or stored to disk.
 */
class VoiceTriggerDetector(
    private val context: Context,
    private val onEmergencyPhraseDetected: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var customPhrase: String = ""
    private var isSessionActive = false
    private var isListeningNow = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRestartRunnable: Runnable? = null

    private val defaultPhrases = listOf("help me", "emergency", "save me", "help", "danger")

    private val _voiceStatus = MutableStateFlow(VoiceStatus.OFF)
    val voiceStatus: StateFlow<VoiceStatus> = _voiceStatus.asStateFlow()

    private val _lastDetectedPhrase = MutableStateFlow<String?>(null)
    val lastDetectedPhrase: StateFlow<String?> = _lastDetectedPhrase.asStateFlow()

    fun updateCustomPhrase(phrase: String) {
        customPhrase = phrase.trim().lowercase()
    }

    fun startListening(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            _voiceStatus.value = VoiceStatus.UNAVAILABLE
            return
        }

        isSessionActive = true
        _voiceStatus.value = VoiceStatus.LISTENING

        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    initRecognizer()
                } else {
                    _voiceStatus.value = VoiceStatus.UNAVAILABLE
                }
            } catch (_: Exception) {
                _voiceStatus.value = VoiceStatus.UNAVAILABLE
            }
        }
    }

    fun stopListening() {
        isSessionActive = false
        isListeningNow = false
        cancelPendingRestart()
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}
        }
        _voiceStatus.value = VoiceStatus.OFF
    }

    private fun cancelPendingRestart() {
        pendingRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRestartRunnable = null
    }

    private fun scheduleCleanRestart(delayMs: Long = 2000L) {
        if (!isSessionActive) return
        cancelPendingRestart()
        pendingRestartRunnable = Runnable {
            if (isSessionActive) {
                startListeningIntent()
            }
        }
        mainHandler.postDelayed(pendingRestartRunnable!!, delayMs)
    }

    /**
     * Trigger simulated emergency voice phrase for testing or demo mode.
     */
    fun simulateVoiceTrigger(phrase: String = "Help me", notifyListener: Boolean = true) {
        _lastDetectedPhrase.value = phrase
        _voiceStatus.value = VoiceStatus.TRIGGER_DETECTED
        if (notifyListener) {
            onEmergencyPhraseDetected(phrase)
        }

        cancelPendingRestart()
        mainHandler.postDelayed({
            if (isSessionActive) {
                _voiceStatus.value = VoiceStatus.LISTENING
            }
        }, 3000L)
    }

    private fun initRecognizer() {
        if (!isSessionActive) return
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (isSessionActive) {
                            isListeningNow = true
                            _voiceStatus.value = VoiceStatus.LISTENING
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        // User began speaking
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        isListeningNow = false
                    }

                    override fun onError(error: Int) {
                        isListeningNow = false
                        if (!isSessionActive) return

                        when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                _voiceStatus.value = VoiceStatus.UNAVAILABLE
                                return
                            }
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                try {
                                    speechRecognizer?.cancel()
                                } catch (_: Exception) {}
                                scheduleCleanRestart(2500L)
                            }
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                // Normal cadence when user is silent while walking
                                scheduleCleanRestart(2000L)
                            }
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                                // On-device speech recognition or offline fallback
                                scheduleCleanRestart(3000L)
                            }
                            else -> {
                                scheduleCleanRestart(2500L)
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListeningNow = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        checkMatches(matches)
                        if (isSessionActive) {
                            scheduleCleanRestart(1500L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        checkMatches(matches)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }

            startListeningIntent()
        } catch (_: Exception) {
            _voiceStatus.value = VoiceStatus.UNAVAILABLE
        }
    }

    private fun startListeningIntent() {
        if (!isSessionActive || speechRecognizer == null) return
        try {
            speechRecognizer?.cancel()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            _voiceStatus.value = VoiceStatus.UNAVAILABLE
        }
    }

    private fun checkMatches(matches: List<String>?) {
        if (matches.isNullOrEmpty()) return
        val targetPhrases = mutableListOf<String>()
        targetPhrases.addAll(defaultPhrases)
        if (customPhrase.isNotBlank()) {
            targetPhrases.add(customPhrase)
        }

        for (spoken in matches) {
            val normalized = spoken.lowercase().trim()
            for (target in targetPhrases) {
                if (normalized.contains(target)) {
                    _lastDetectedPhrase.value = spoken
                    _voiceStatus.value = VoiceStatus.TRIGGER_DETECTED
                    onEmergencyPhraseDetected(target)
                    return
                }
            }
        }
    }
}
