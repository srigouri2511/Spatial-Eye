package com.spatialmemory.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a user's habitual walking corridor through a mapped [Place].
 *
 * A WalkCorridor stores the sequence of 2D floor-plane coordinates (X, Z) frequently traversed by the visually
 * impaired user. The alerting engine intersects newly detected obstacles or moved objects against these corridors:
 * objects falling within a corridor or its safety buffer width trigger immediate auditory/haptic warnings.
 *
 * @property id Unique string identifier for the walking corridor.
 * @property placeId Foreign key referencing the parent [Place].
 * @property pathPoints JSON-encoded list of 2D floor coordinates `(X, Z)` representing the polyline path of the user's walk path.
 * @property widthMeters Clearance width of the corridor (in meters). Defines the buffer zone around [pathPoints] for hazard detection.
 */
@Entity(
    tableName = "walk_corridors",
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
data class WalkCorridor(
    @PrimaryKey
    val id: String,
    val placeId: String,
    val pathPoints: List<Pair<Float, Float>>,
    val widthMeters: Float
)
