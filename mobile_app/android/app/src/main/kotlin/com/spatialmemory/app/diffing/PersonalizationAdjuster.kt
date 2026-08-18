package com.spatialmemory.app.diffing

import com.spatialmemory.app.data.SpatialMemoryDatabase
import com.spatialmemory.app.data.UserFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Personalized alerting threshold adjustments computed from user feedback logs.
 *
 * @property minSeverityToAlert Minimum severity score required to generate an active user alert for this place.
 * @property maxCorridorDistanceMeters Effective corridor distance cutoff (in meters) outside of which alerts are suppressed.
 */
data class PersonalizedThresholds(
    val minSeverityToAlert: Float = AlertThresholds.SEVERITY_SILENT_MAX,
    val maxCorridorDistanceMeters: Float = ChangeDetectionEngine.CORRIDOR_MAX_DIST_EFFECT_METERS
)

/**
 * Personalization learning loop for adjusting spatial change alert sensitivity per [Place].
 *
 * Inspects historical [com.spatialmemory.app.data.ChangeEvent] records in Room memory where users submitted
 * [UserFeedback.NOT_USEFUL] feedback. Adaptively tightens corridor distance boundaries and elevates severity
 * thresholds to eliminate nuisance alarms and tailor alert sensitivity to individual preference.
 *
 * @param database [SpatialMemoryDatabase] instance for querying change event feedback logs.
 */
class PersonalizationAdjuster(private val database: SpatialMemoryDatabase) {

    /**
     * Calculates personalized alert thresholds for a given place ID based on accumulated user feedback.
     *
     * Heuristic Learning Rules:
     * 1. If >= 3 past change events in this place were flagged as [UserFeedback.NOT_USEFUL], elevate the minimum
     *    severity required to alert from 0.35f up to 0.50f.
     * 2. If >= 5 past change events were flagged as [UserFeedback.NOT_USEFUL], tighten the effective corridor
     *    distance cutoff from 2.0m down to 1.0m.
     *
     * TODO: Replace this lightweight rule heuristic with a logistic-regression online SGD weight update
     * model in future releases to continuously tune feature weights (distance, height, confidence).
     *
     * @param placeId Unique place identifier.
     * @return [PersonalizedThresholds] containing adapted alerting cutoffs.
     */
    suspend fun adjustThresholds(placeId: String): PersonalizedThresholds = withContext(Dispatchers.IO) {
        val recentEvents = database.changeEventDao()
            .getRecentChangeEventsForPlace(placeId, 0L)
            .firstOrNull() ?: emptyList()

        val notUsefulCount = recentEvents.count { it.userFeedback == UserFeedback.NOT_USEFUL }

        var adaptedMinSeverity = AlertThresholds.SEVERITY_SILENT_MAX
        var adaptedMaxDistance = ChangeDetectionEngine.CORRIDOR_MAX_DIST_EFFECT_METERS

        if (notUsefulCount >= 3) {
            // User frequently flags alerts as nuisance noise -> raise alert severity floor
            adaptedMinSeverity = 0.50f
        }

        if (notUsefulCount >= 5) {
            // User prefers alerts strictly limited to immediate walk corridor -> tighten distance cutoff
            adaptedMaxDistance = 1.0f
        }

        PersonalizedThresholds(
            minSeverityToAlert = adaptedMinSeverity,
            maxCorridorDistanceMeters = adaptedMaxDistance
        )
    }
}
