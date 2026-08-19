package com.spatialmemory.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Sealed class representing recognized user voice intent queries.
 */
sealed class VoiceIntent {
    /** Request for spatial change summary in current place since last visit. */
    object WhatsNewHere : VoiceIntent()

    /** Request for spatial change summary since a specific timeframe (e.g., "yesterday"). */
    data class WhatsNewSince(val timeframe: String?) : VoiceIntent()

    /** Request to initiate room mapping flow for a place. */
    data class StartMapping(val placeName: String?) : VoiceIntent()

    /** Request for current place identification. */
    object WhereAmI : VoiceIntent()

    /** Request to re-read the most recent spatial change alert. */
    object ReadLastAlert : VoiceIntent()

    /** Fallback when speech utterance could not be mapped to a known intent. */
    data class Unknown(val rawText: String) : VoiceIntent()
}

/**
 * Handles active voice query listening and rule-based intent classification.
 *
 * Distinct from short post-alert feedback listening, [VoiceQueryManager] provides an on-demand
 * command listener triggered by physical gestures (e.g., long-press volume button or screen double-tap).
 *
 * @param context Application context.
 */
class VoiceQueryManager(private val context: Context) : AutoCloseable {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Listens for an active voice query command from the user.
     *
     * @param timeoutMs Maximum listening duration in milliseconds (default 6000ms).
     * @return Recognized speech text or null on timeout/failure.
     */
    suspend fun listenForCommand(timeoutMs: Long = 6000L): String? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<String?> { continuation ->
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (continuation.isActive) {
                            continuation.resume(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                continuation.invokeOnCancellation {
                    recognizer.destroy()
                }

                try {
                    recognizer.startListening(intent)
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }

    /**
     * Classifies a raw speech utterance into a structured [VoiceIntent].
     *
     * Uses straightforward keyword and substring matching against [VoiceCommandGrammar].
     *
     * @param utterance Raw recognized speech string.
     * @return Classified [VoiceIntent].
     */
    fun classifyIntent(utterance: String): VoiceIntent {
        val lower = utterance.lowercase().trim()

        // 1. Where am I
        if (VoiceCommandGrammar.WHERE_AM_I_PHRASES.any { lower.contains(it) }) {
            return VoiceIntent.WhereAmI
        }

        // 2. Read last alert / repeat
        if (VoiceCommandGrammar.READ_LAST_ALERT_PHRASES.any { lower.contains(it) }) {
            return VoiceIntent.ReadLastAlert
        }

        // 3. Start mapping
        if (VoiceCommandGrammar.START_MAPPING_PHRASES.any { lower.contains(it) }) {
            val placeName = extractPlaceName(lower)
            return VoiceIntent.StartMapping(placeName)
        }

        // 4. What's new since (timeframe)
        val sinceMatch = VoiceCommandGrammar.WHATS_NEW_SINCE_PHRASES.firstOrNull { lower.contains(it) }
        if (sinceMatch != null) {
            return VoiceIntent.WhatsNewSince(sinceMatch)
        }

        // 5. What's new here / general status
        if (VoiceCommandGrammar.WHATS_NEW_PHRASES.any { lower.contains(it) }) {
            return VoiceIntent.WhatsNewHere
        }

        return VoiceIntent.Unknown(utterance)
    }

    private fun extractPlaceName(utterance: String): String? {
        val keywords = listOf("for", "place", "room", "called")
        for (kw in keywords) {
            if (utterance.contains(kw)) {
                val part = utterance.substringAfter(kw).trim()
                if (part.isNotEmpty()) return part
            }
        }
        return null
    }

    fun release() {
        close()
    }

    override fun close() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
