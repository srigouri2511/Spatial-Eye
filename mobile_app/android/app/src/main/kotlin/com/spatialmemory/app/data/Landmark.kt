package com.spatialmemory.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Defines the spatial stability expectation of a mapped landmark object.
 *
 * This classification directly drives the selective alerting system for visually impaired users:
 * - [STATIC]: Fixed structural landmarks (e.g., walls, structural pillars, doorways, staircase edges).
 *   Movement in a STATIC landmark indicates either a severe structural hazard or a SLAM relocalization failure.
 *   Generates critical-level alerts.
 * - [SEMI_STATIC]: Semi-permanent furniture (e.g., sofas, heavy dining tables, desks).
 *   Stable day-to-day, but occasionally rearranged. Generates moderate-level informational alerts when moved.
 * - [DYNAMIC]: Highly transient or movable objects (e.g., office chairs, shoes, backpacks, delivery boxes).
 *   High expected position variance. Alerts are selectively generated only if the object obstructs a walking corridor
 *   or poses an active trip hazard.
 */
enum class PermanenceClass {
    STATIC,
    SEMI_STATIC,
    DYNAMIC
}

/**
 * Entity representing a recognized 3D landmark or object anchored within a mapped Place.
 *
 * Landmarks form the baseline spatial memory map of the user's environment. Subsequent visual scans
 * diff observed objects against saved landmarks to detect moved, added, or missing obstacles.
 *
 * @property id Unique string identifier for the landmark object.
 * @property placeId Foreign key linking to the parent [Place].
 * @property label Semantic object label detected by TFLite (e.g., "sofa", "chair", "doorway", "staircase_edge").
 * @property positionX X coordinate (in meters) within the AR world map's local coordinate frame.
 * @property positionY Y coordinate (in meters, vertical height offset) within the AR world map frame.
 * @property positionZ Z coordinate (in meters) within the AR world map's local coordinate frame.
 * @property permanenceClass Spatial permanence expectation ([PermanenceClass.STATIC], [PermanenceClass.SEMI_STATIC], [PermanenceClass.DYNAMIC]), driving alert severity calculation.
 * @property visitsConfirmed Number of independent scanning sessions where this object was re-observed at roughly the same position. Higher values indicate higher baseline map confidence.
 * @property lastSeenAt Epoch timestamp (in milliseconds) when this landmark was last confirmed present.
 * @property firstSeenAt Epoch timestamp (in milliseconds) when this landmark was first added to spatial memory.
 * @property boundingBoxHeight Height of the object's 3D bounding box (in meters). Crucial for distinguishing low trip hazards (e.g., low stools, shoes) from overhead obstacles (e.g., hanging signs, open cabinet doors).
 * @property detectionConfidence TFLite model detection confidence score ranging from 0.0 to 1.0.
 */
@Entity(
    tableName = "landmarks",
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["placeId"])]
)
data class Landmark(
    @PrimaryKey
    val id: String,
    val placeId: String,
    val label: String,
    val positionX: Float,
    val positionY: Float,
    val positionZ: Float,
    val permanenceClass: PermanenceClass,
    val visitsConfirmed: Int,
    val lastSeenAt: Long,
    val firstSeenAt: Long,
    val boundingBoxHeight: Float,
    val detectionConfidence: Float
)
