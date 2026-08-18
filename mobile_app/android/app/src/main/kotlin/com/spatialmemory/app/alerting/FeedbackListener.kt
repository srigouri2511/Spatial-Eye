package com.spatialmemory.app.alerting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.spatialmemory.app.data.SpatialMemoryDatabase
import com.spatialmemory.app.data.UserFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Listens for brief spoken voice feedback from the user immediately following an alert delivery.
 *
 * Captures user responses ("yes", "helpful", "not helpful", "thanks") via Android [SpeechRecognizer]
 * and records the response into Room memory ([UserFeedback.USEFUL] or [UserFeedback.NOT_USEFUL]).
 *
 * ### Personalization Loop Note:
 * Recorded [UserFeedback] entries are consumed by [com.spatialmemory.app.diffing.PersonalizationAdjuster]
 * to dynamically tune corridor distance cutoffs and alerting severity thresholds per place.
 *
 * @param context Application context.
 * @param database [SpatialMemoryDatabase] instance for persisting user feedback.
 */
class FeedbackListener(
    private val context: Context,
    private val database: SpatialMemoryDatabase
) : AutoCloseable {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Listens briefly for a spoken user feedback response following an alert.
     *
     * Non-blocking coroutine bridge wrapping [SpeechRecognizer] with a timeout.
     * If no speech is recognized within [timeoutMs], completes silently without throwing errors.
     *
     * @param changeEventId Target change event identifier in Room memory.
     * @param timeoutMs Maximum duration (in milliseconds) to listen for speech (default 4000ms).
     * @return Recognized [UserFeedback] or null if timeout/unrecognized.
     */
    suspend fun listenForFeedback(changeEventId: String, timeoutMs: Long = 4000L): UserFeedback? = withContext(Dispatchers.Main) {
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<UserFeedback?> { continuation ->
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
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
                        val feedback = parseFeedbackMatches(matches)
                        if (continuation.isActive) {
                            continuation.resume(feedback)
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

        // If valid feedback was captured, update Room database
        if (result != null) {
            withContext(Dispatchers.IO) {
                database.changeEventDao().updateUserFeedback(changeEventId, result)
            }
        }

        result
    }

    private fun parseFeedbackMatches(matches: ArrayList<String>?): UserFeedback? {
        if (matches == null) return null
        for (text in matches) {
            val lower = text.lowercase()
            if (lower.contains("yes") || lower.contains("helpful") || lower.contains("useful") || lower.contains("thanks") || lower.contains("good")) {
                return UserFeedback.USEFUL
            }
            if (lower.contains("no") || lower.contains("not helpful") || lower.contains("unhelpful") || lower.contains("stop") || lower.contains("wrong") || lower.contains("bad")) {
                return UserFeedback.NOT_USEFUL
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
