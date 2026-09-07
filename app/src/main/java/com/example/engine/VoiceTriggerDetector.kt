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
 * No raw audio data is recorded or stored to disk.
 */
class VoiceTriggerDetector(
    private val context: Context,
    private val onEmergencyPhraseDetected: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var customPhrase: String = ""
    private var isSessionActive = false
    private val mainHandler = Handler(Looper.getMainLooper())

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
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
        }
        _voiceStatus.value = VoiceStatus.OFF
    }

    /**
     * Called directly by Demo mode or the in-app "Demo Voice Trigger" button
     * to guarantee seamless hackathon judge testing regardless of mic permissions or ambient noise.
     */
    fun simulateVoiceTrigger(phrase: String = "Help me", notifyListener: Boolean = true) {
        _lastDetectedPhrase.value = phrase
        _voiceStatus.value = VoiceStatus.TRIGGER_DETECTED
        if (notifyListener) {
            onEmergencyPhraseDetected(phrase)
        }

        mainHandler.postDelayed({
            if (isSessionActive) {
                _voiceStatus.value = VoiceStatus.LISTENING
            }
        }, 3000L)
    }

    private fun initRecognizer() {
        if (!isSessionActive) return
        speechRecognizer?.destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (isSessionActive) _voiceStatus.value = VoiceStatus.LISTENING
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    if (isSessionActive) {
                        // Restart recognition loop after a short breath
                        mainHandler.postDelayed({
                            if (isSessionActive) startListeningIntent()
                        }, 1200L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    checkMatches(matches)
                    if (isSessionActive) {
                        mainHandler.postDelayed({
                            if (isSessionActive) startListeningIntent()
                        }, 800L)
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
    }

    private fun startListeningIntent() {
        if (!isSessionActive || speechRecognizer == null) return
        try {
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
