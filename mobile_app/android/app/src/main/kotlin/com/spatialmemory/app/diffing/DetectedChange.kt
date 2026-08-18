package com.spatialmemory.app.diffing

import com.spatialmemory.app.data.ChangeEventType
import com.spatialmemory.app.data.Landmark
import com.spatialmemory.app.detection.SpatialDetection

/**
 * Represents a meaningful spatial delta evaluated by the [ChangeDetectionEngine].
 *
 * Encapsulates the detected change type, associated baseline landmark, live 3D detection,
 * calculated safety severity score, corridor proximity distance, and a human-readable spoken alert explanation.
 *
 * @property eventType Type of spatial change ([ChangeEventType.NEW_OBJECT], [ChangeEventType.OBJECT_MOVED], [ChangeEventType.OBJECT_MISSING], [ChangeEventType.LANDMARK_ALTERED]).
 * @property landmark Baseline [Landmark] object from spatial memory (null for [ChangeEventType.NEW_OBJECT]).
 * @property liveDetection Live 3D [SpatialDetection] from camera feed (null for [ChangeEventType.OBJECT_MISSING]).
 * @property severityScore Calculated hazard severity score ranging from 0.0 (negligible) to 1.0 (critical hazard).
 * @property distanceFromCorridorMeters Perpendicular distance (in meters) from the object to the nearest habitual walking corridor floor path.
 * @property explanation Human-readable spoken alert string designed for auditory feedback to visually impaired users (e.g., "Chair moved 0.4m into your usual path.").
 */
data class DetectedChange(
    val eventType: ChangeEventType,
    val landmark: Landmark?,
    val liveDetection: SpatialDetection?,
    val severityScore: Float,
    val distanceFromCorridorMeters: Float,
    val explanation: String
)
