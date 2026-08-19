package com.spatialmemory.app.diffing

import com.spatialmemory.app.data.ChangeEvent
import com.spatialmemory.app.data.ChangeEventType
import com.spatialmemory.app.data.Landmark
import com.spatialmemory.app.data.PermanenceClass
import com.spatialmemory.app.data.Place
import com.spatialmemory.app.data.SpatialMemoryDatabase
import com.spatialmemory.app.data.WalkCorridor
import com.spatialmemory.app.detection.SpatialDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Core spatial diffing engine for the Spatial Memory Assistant.
 *
 * Compares live 3D object detections against baseline spatial landmarks stored in Room memory.
 * Evaluates spatial deltas, computes floor-plane distance to user walking corridors, and calculates
 * multi-factor safety severity scores to drive selective alerting for visually impaired users.
 *
 * @param database [SpatialMemoryDatabase] instance for updating landmark visit confirmations and logging change events.
 */
class ChangeDetectionEngine(private val database: SpatialMemoryDatabase) {

    companion object {
        /** Maximum 3D Euclidean distance (in meters) to match a live detection with a baseline landmark of the same label. */
        const val MATCHING_RADIUS_METERS: Float = 0.50f

        /** Distance delta (in meters) above which a matched landmark is classified as [ChangeEventType.OBJECT_MOVED]. */
        const val MOVEMENT_THRESHOLD_METERS: Float = 0.30f

        /** Minimum visit confirmation count required before flagging a missing static/semi-static landmark as [ChangeEventType.OBJECT_MISSING]. */
        const val MIN_CONFIRMED_VISITS: Int = 3

        /** Maximum floor-plane corridor distance (in meters) over which corridor proximity influences severity score. */
        const val CORRIDOR_MAX_DIST_EFFECT_METERS: Float = 2.0f

        // --- Severity Weight Constants ---
        const val WEIGHT_CORRIDOR_PROXIMITY: Float = 0.45f
        const val WEIGHT_HEIGHT_HAZARD: Float = 0.25f
        const val WEIGHT_DETECTION_CONFIDENCE: Float = 0.15f
        const val HIGH_TRAFFIC_MULTIPLIER: Float = 1.25f
    }

    /**
     * Executes spatial diffing between live 3D detections and stored place landmarks.
     *
     * @param place Active [Place] entity.
     * @param liveDetections List of 3D [SpatialDetection] objects from current camera feed scan.
     * @param storedLandmarks Baseline [Landmark] objects registered for this place.
     * @param corridors Habitual [WalkCorridor] polylines traversed by the user in this place.
     * @return List of scored [DetectedChange] objects representing meaningful spatial changes.
     */
    suspend fun diff(
        place: Place,
        liveDetections: List<SpatialDetection>,
        storedLandmarks: List<Landmark>,
        corridors: List<WalkCorridor>
    ): List<DetectedChange> = withContext(Dispatchers.Default) {
        val detectedChanges = mutableListOf<DetectedChange>()
        val matchedLandmarkIds = mutableSetOf<String>()
        val now = System.currentTimeMillis()

        // -------------------------------------------------------------
        // Step 1: Process Live Detections (New Objects & Moved Objects)
        // -------------------------------------------------------------
        for (live in liveDetections) {
            val label = live.detection.label.lowercase()

            // Filter stored landmarks with label parity
            val candidates = storedLandmarks.filter { it.label.lowercase() == label }

            // Find closest candidate within MATCHING_RADIUS_METERS
            var bestMatch: Landmark? = null
            var minDistance = Float.MAX_VALUE

            for (candidate in candidates) {
                val dx = live.worldPosition[0] - candidate.positionX
                val dy = live.worldPosition[1] - candidate.positionY
                val dz = live.worldPosition[2] - candidate.positionZ
                val dist = sqrt(dx * dx + dy * dy + dz * dz)

                if (dist <= MATCHING_RADIUS_METERS && dist < minDistance) {
                    minDistance = dist
                    bestMatch = candidate
                }
            }

            val corridorDist = distanceFromCorridor(live.worldPosition, corridors)

            if (bestMatch == null) {
                // Classification: NEW_OBJECT (No matching baseline landmark nearby)
                val severity = computeSeverity(
                    eventType = ChangeEventType.NEW_OBJECT,
                    permanenceClass = null,
                    distanceFromCorridor = corridorDist,
                    estimatedHeightMeters = live.estimatedHeightMeters,
                    detectionConfidence = live.detection.confidence,
                    isHighTrafficCorridor = true
                )

                val explanation = if (corridorDist <= 0.1f) {
                    "New ${live.detection.label} blocking your walking path."
                } else {
                    "New ${live.detection.label} detected ${String.format("%.1f", corridorDist)} meters from your path."
                }

                val change = DetectedChange(
                    eventType = ChangeEventType.NEW_OBJECT,
                    landmark = null,
                    liveDetection = live,
                    severityScore = severity,
                    distanceFromCorridorMeters = corridorDist,
                    explanation = explanation
                )
                detectedChanges.add(change)

            } else {
                matchedLandmarkIds.add(bestMatch.id)

                if (minDistance > MOVEMENT_THRESHOLD_METERS) {
                    // Classification: OBJECT_MOVED (Position shifted beyond threshold)
                    val severity = computeSeverity(
                        eventType = ChangeEventType.OBJECT_MOVED,
                        permanenceClass = bestMatch.permanenceClass,
                        distanceFromCorridor = corridorDist,
                        estimatedHeightMeters = live.estimatedHeightMeters,
                        detectionConfidence = live.detection.confidence,
                        isHighTrafficCorridor = true
                    )

                    val explanation = "${bestMatch.label.capitalize()} moved ${String.format("%.1f", minDistance)} meters into your path area."

                    val change = DetectedChange(
                        eventType = ChangeEventType.OBJECT_MOVED,
                        landmark = bestMatch,
                        liveDetection = live,
                        severityScore = severity,
                        distanceFromCorridorMeters = corridorDist,
                        explanation = explanation
                    )
                    detectedChanges.add(change)

                } else {
                    // Stable Memory Path: Landmark re-confirmed in baseline location
                    // Increment visit count and update last seen timestamp in database
                    database.landmarkDao().incrementVisitsAndSeen(bestMatch.id, now)
                }
            }
        }

        // -------------------------------------------------------------
        // Step 2: Process Missing Landmarks (OBJECT_MISSING)
        // -------------------------------------------------------------
        for (landmark in storedLandmarks) {
            val isMissingCandidate = !matchedLandmarkIds.contains(landmark.id) &&
                    (landmark.permanenceClass == PermanenceClass.STATIC || landmark.permanenceClass == PermanenceClass.SEMI_STATIC) &&
                    landmark.visitsConfirmed >= MIN_CONFIRMED_VISITS

            if (isMissingCandidate) {
                val landmarkPos = floatArrayOf(landmark.positionX, landmark.positionY, landmark.positionZ)
                val corridorDist = distanceFromCorridor(landmarkPos, corridors)

                // Only generate OBJECT_MISSING alerts if the missing item is near a walking corridor (e.g. handrail, doorway)
                if (corridorDist <= CORRIDOR_MAX_DIST_EFFECT_METERS) {
                    val severity = computeSeverity(
                        eventType = ChangeEventType.OBJECT_MISSING,
                        permanenceClass = landmark.permanenceClass,
                        distanceFromCorridor = corridorDist,
                        estimatedHeightMeters = landmark.boundingBoxHeight,
                        detectionConfidence = 1.0f,
                        isHighTrafficCorridor = true
                    )

                    val explanation = "Expected ${landmark.label} is missing near your path."

                    val change = DetectedChange(
                        eventType = ChangeEventType.OBJECT_MISSING,
                        landmark = landmark,
                        liveDetection = null,
                        severityScore = severity,
                        distanceFromCorridorMeters = corridorDist,
                        explanation = explanation
                    )
                    detectedChanges.add(change)
                }
            }
        }

        // -------------------------------------------------------------
        // Step 3: Log Detected Change Events into Room Database
        // -------------------------------------------------------------
        for (change in detectedChanges) {
            val changeEvent = ChangeEvent(
                id = UUID.randomUUID().toString(),
                placeId = place.id,
                landmarkId = change.landmark?.id,
                eventType = change.eventType,
                severityScore = change.severityScore,
                detectedAt = now,
                userFeedback = null
            )
            database.changeEventDao().insertChangeEvent(changeEvent)
        }

        detectedChanges
    }

    /**
     * Calculates the minimum 2D floor-plane `(X, Z)` perpendicular distance from a 3D point to any corridor path polyline.
     *
     * Subtracts the corridor's `widthMeters` clearance band, clamping negative distances (points inside the corridor) to `0.0f`.
     *
     * @param position 3D coordinate float array `[x, y, z]`.
     * @param corridors List of [WalkCorridor] entities.
     * @return Perpendicular distance in meters from corridor boundary.
     */
    fun distanceFromCorridor(position: FloatArray, corridors: List<WalkCorridor>): Float {
        if (corridors.isEmpty()) return CORRIDOR_MAX_DIST_EFFECT_METERS

        val px = position[0]
        val pz = position[2]

        var minDistanceToBoundary = Float.MAX_VALUE

        for (corridor in corridors) {
            val path = corridor.pathPoints
            if (path.isEmpty()) continue

            var minCenterlineDist = Float.MAX_VALUE

            if (path.size == 1) {
                val dx = px - path[0].first
                val dz = pz - path[0].second
                minCenterlineDist = sqrt(dx * dx + dz * dz)
            } else {
                for (i in 0 until path.size - 1) {
                    val distSegment = distToSegment(
                        px, pz,
                        path[i].first, path[i].second,
                        path[i + 1].first, path[i + 1].second
                    )
                    if (distSegment < minCenterlineDist) {
                        minCenterlineDist = distSegment
                    }
                }
            }

            // Subtract half corridor width (clearance boundary)
            val halfWidth = corridor.widthMeters / 2.0f
            val distToCorridorBoundary = max(0.0f, minCenterlineDist - halfWidth)

            if (distToCorridorBoundary < minDistanceToBoundary) {
                minDistanceToBoundary = distToCorridorBoundary
            }
        }

        return if (minDistanceToBoundary == Float.MAX_VALUE) CORRIDOR_MAX_DIST_EFFECT_METERS else minDistanceToBoundary
    }

    private fun distToSegment(px: Float, pz: Float, x1: Float, z1: Float, x2: Float, z2: Float): Float {
        val dx = x2 - x1
        val dz = z2 - z1
        if (dx == 0.0f && dz == 0.0f) {
            val dX = px - x1
            val dZ = pz - z1
            return sqrt(dX * dX + dZ * dZ)
        }

        val t = ((px - x1) * dx + (pz - z1) * dz) / (dx * dx + dz * dz)
        val tClamped = t.coerceIn(0.0f, 1.0f)

        val projX = x1 + tClamped * dx
        val projZ = z1 + tClamped * dz

        val diffX = px - projX
        val diffZ = pz - projZ
        return sqrt(diffX * diffX + diffZ * diffZ)
    }

    /**
     * Calculates the safety hazard severity score ranging from 0.0 (negligible) to 1.0 (critical hazard).
     *
     * ### Severity Scoring Formula Rationale:
     * 1. **Corridor Proximity Factor (0.0 to 1.0)**: Objects directly inside or adjacent to a walking path
     *    pose an immediate collision risk. Score decays linearly to 0.0 at 2.0m distance.
     * 2. **Height Hazard Piecewise Model (0.0 to 1.0)**:
     *    - Ground trip hazards (`0.1m <= height <= 0.8m` e.g., low stools, shoes, boxes) score high (~0.90)
     *      because visually impaired users cannot easily detect them with white canes.
     *    - Overhead/head hazards (`height >= 1.5m` e.g., open cabinet doors, low arches) score high (~0.95)
     *      due to head injury risk.
     *    - Mid-range waist clutter (`0.8m < height < 1.5m`) scores lower (~0.40) as it is readily detected by tactile canes.
     * 3. **Permanence Class Boost**: Moving a [PermanenceClass.STATIC] landmark (structural element) adds +0.15 severity;
     *    moving a [PermanenceClass.SEMI_STATIC] landmark adds +0.05.
     * 4. **Detection Confidence Weight**: Low confidence detections scale down severity to prevent false alarms.
     *
     * @param eventType Type of change.
     * @param permanenceClass Stability classification of target landmark.
     * @param distanceFromCorridor Floor-plane distance to corridor boundary in meters.
     * @param estimatedHeightMeters Height of object bounding box in meters.
     * @param detectionConfidence Model detection confidence score (0.0 to 1.0).
     * @param isHighTrafficCorridor True if corridor represents a high-frequency path.
     * @return Final calculated severity score clamped to [0.0f, 1.0f].
     */
    fun computeSeverity(
        eventType: ChangeEventType,
        permanenceClass: PermanenceClass?,
        distanceFromCorridor: Float,
        estimatedHeightMeters: Float,
        detectionConfidence: Float,
        isHighTrafficCorridor: Boolean
    ): Float {
        // Factor 1: Corridor Proximity (linear decay from 1.0 inside corridor to 0.0 at 2.0m away)
        val proximityFactor = max(0.0f, 1.0f - (distanceFromCorridor / CORRIDOR_MAX_DIST_EFFECT_METERS))

        // Factor 2: Piecewise Height Hazard Score
        val heightHazardFactor = when {
            estimatedHeightMeters in 0.10f..0.80f -> 0.90f // Ground trip hazard (boxes, shoes, stools)
            estimatedHeightMeters >= 1.50f -> 0.95f        // Head-height hazard (open cabinets, hanging items)
            else -> 0.40f                                  // Mid-range clutter (detected by white cane)
        }

        // Factor 3: Permanence Modifier
        val permanenceModifier = when (permanenceClass) {
            PermanenceClass.STATIC -> 0.15f
            PermanenceClass.SEMI_STATIC -> 0.05f
            PermanenceClass.DYNAMIC, null -> 0.0f
        }

        // Combine weighted components
        var baseScore = (proximityFactor * WEIGHT_CORRIDOR_PROXIMITY) +
                (heightHazardFactor * WEIGHT_HEIGHT_HAZARD) +
                (detectionConfidence * WEIGHT_DETECTION_CONFIDENCE) +
                permanenceModifier

        // High traffic corridor boost multiplier
        if (isHighTrafficCorridor) {
            baseScore *= HIGH_TRAFFIC_MULTIPLIER
        }

        return baseScore.coerceIn(0.0f, 1.0f)
    }

    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
