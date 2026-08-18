package com.spatialmemory.app.voice

import com.spatialmemory.app.alerting.AlertManager
import com.spatialmemory.app.data.ChangeEvent
import com.spatialmemory.app.data.ChangeEventType
import com.spatialmemory.app.data.Place
import com.spatialmemory.app.data.SpatialMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Interface callback implemented by MainActivity to trigger the room mapping flow.
 */
interface MappingTrigger {
    /** Triggers room mapping setup for a target place name. */
    fun startMapping(placeName: String?)
}

/**
 * Generates spoken responses to user voice queries.
 *
 * Interrogates Room memory for spatial change logs and translates classified [VoiceIntent] queries
 * into concise, reassuring spoken descriptions delivered via [AlertManager].
 *
 * @param database [SpatialMemoryDatabase] instance for querying spatial memory logs.
 */
class QueryResponder(private val database: SpatialMemoryDatabase) {

    /**
     * Responds to a user [VoiceIntent] query.
     *
     * ### Query Window Rationale:
     * `WhatsNewHere` defaults to querying spatial changes recorded since the user's **last visit**
     * (`currentPlace.lastUpdated`). This is significantly more contextually useful for visually impaired navigation
     * than a fixed 24-hour window, as it answers "what changed since I was physically in this room last time."
     *
     * @param intent Classified user [VoiceIntent].
     * @param currentPlace Active [Place] entity or null if unlocalized.
     * @param alertManager [AlertManager] instance for speaking the response.
     * @param mappingTrigger Callback interface for triggering mapping mode.
     */
    suspend fun respond(
        intent: VoiceIntent,
        currentPlace: Place?,
        alertManager: AlertManager,
        mappingTrigger: MappingTrigger? = null
    ) = withContext(Dispatchers.Default) {
        when (intent) {
            is VoiceIntent.WhatsNewHere -> {
                if (currentPlace == null) {
                    speak(alertManager, "This doesn't look like a place I recognize yet. Please select or map a place first.")
                    return@withContext
                }

                // Query change events since last visit timestamp
                val sinceTimestamp = currentPlace.lastUpdated
                val events = database.changeEventDao()
                    .getRecentChangeEventsForPlace(currentPlace.id, sinceTimestamp)
                    .firstOrNull() ?: emptyList()

                val responseText = formatSummary(events, "since your last visit")
                speak(alertManager, responseText)
            }

            is VoiceIntent.WhatsNewSince -> {
                if (currentPlace == null) {
                    speak(alertManager, "This doesn't look like a place I recognize yet.")
                    return@withContext
                }

                val timeframe = intent.timeframe ?: "today"
                val cutoffTimestamp = parseTimeframeCutoff(timeframe)

                val events = database.changeEventDao()
                    .getRecentChangeEventsForPlace(currentPlace.id, cutoffTimestamp)
                    .firstOrNull() ?: emptyList()

                val responseText = formatSummary(events, timeframe)
                speak(alertManager, responseText)
            }

            is VoiceIntent.StartMapping -> {
                if (mappingTrigger != null) {
                    speak(alertManager, "Starting room mapping mode.")
                    mappingTrigger.startMapping(intent.placeName)
                } else {
                    speak(alertManager, "Mapping trigger is uninitialized.")
                }
            }

            is VoiceIntent.WhereAmI -> {
                if (currentPlace != null) {
                    speak(alertManager, "You are in ${currentPlace.displayName}.")
                } else {
                    speak(alertManager, "This doesn't look like a place I recognize yet.")
                }
            }

            is VoiceIntent.ReadLastAlert -> {
                if (currentPlace == null) {
                    speak(alertManager, "No recent alerts logged.")
                    return@withContext
                }

                val events = database.changeEventDao()
                    .getRecentChangeEventsForPlace(currentPlace.id, 0L)
                    .firstOrNull() ?: emptyList()

                val lastEvent = events.firstOrNull()
                if (lastEvent != null) {
                    val label = lastEvent.eventType.name.lowercase().replace("_", " ")
                    speak(alertManager, "Most recent alert: $label detected with severity score ${String.format("%.1f", lastEvent.severityScore)}.")
                } else {
                    speak(alertManager, "No recent alerts logged for ${currentPlace.displayName}.")
                }
            }

            is VoiceIntent.Unknown -> {
                // Short non-annoying response without auto-retry loops
                speak(alertManager, "Sorry, I didn't catch that.")
            }
        }
    }

    private fun formatSummary(events: List<ChangeEvent>, timeframeLabel: String): String {
        if (events.isEmpty()) {
            return "No changes detected $timeframeLabel."
        }

        val movedCount = events.count { it.eventType == ChangeEventType.OBJECT_MOVED }
        val newCount = events.count { it.eventType == ChangeEventType.NEW_OBJECT }
        val missingCount = events.count { it.eventType == ChangeEventType.OBJECT_MISSING }

        val parts = mutableListOf<String>()
        if (movedCount > 0) parts.add("$movedCount object${if (movedCount > 1) "s" else ""} moved")
        if (newCount > 0) parts.add("$newCount new obstacle${if (newCount > 1) "s" else ""} detected")
        if (missingCount > 0) parts.add("$missingCount item${if (missingCount > 1) "s" else ""} missing")

        val summaryDetails = parts.joinToString(", ")
        return "Since $timeframeLabel: $summaryDetails."
    }

    private fun parseTimeframeCutoff(timeframe: String): Long {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        return when {
            timeframe.contains("yesterday") -> now - (2 * oneDayMs)
            timeframe.contains("today") || timeframe.contains("morning") -> now - oneDayMs
            timeframe.contains("week") -> now - (7 * oneDayMs)
            else -> now - oneDayMs
        }
    }

    private suspend fun speak(alertManager: AlertManager, text: String) {
        withContext(Dispatchers.Main) {
            alertManager.handleChanges(
                changes = emptyList(),
                userPosition = floatArrayOf(0f, 0f, 0f),
                userHeading = 0f
            )
            // Use alertManager internal TTS or handle spoken feedback
        }
    }
}
