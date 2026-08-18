package com.spatialmemory.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a physical location mapped by the Spatial Memory Assistant.
 *
 * A Place serves as the spatial reference frame anchor for registered landmarks,
 * habitual walking corridors, and detected change events.
 *
 * @property id Unique string identifier for the place (e.g., "home_living_room", "office_lobby").
 * @property displayName User-facing name for auditory cues and UI display (e.g., "Living Room").
 * @property lastUpdated Epoch timestamp (in milliseconds) when this place map or its contents were last modified.
 * @property arWorldMapFilePath Absolute path on device disk storing the serialized ARCore ARWorldMap anchor frame.
 * @property createdAt Epoch timestamp (in milliseconds) when this place map was originally created.
 */
@Entity(tableName = "places")
data class Place(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val lastUpdated: Long,
    val arWorldMapFilePath: String,
    val createdAt: Long
)
