package com.spatialmemory.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [Place] entities.
 */
@Dao
interface PlaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: Place)

    @Update
    suspend fun updatePlace(place: Place)

    @Delete
    suspend fun deletePlace(place: Place)

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getPlaceById(id: String): Place?

    @Query("SELECT * FROM places WHERE id = :id")
    fun getPlaceByIdFlow(id: String): Flow<Place?>

    @Query("SELECT * FROM places ORDER BY lastUpdated DESC")
    fun getAllPlaces(): Flow<List<Place>>
}

/**
 * Data Access Object for [Landmark] entities.
 */
@Dao
interface LandmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLandmark(landmark: Landmark)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLandmarks(landmarks: List<Landmark>)

    @Update
    suspend fun updateLandmark(landmark: Landmark)

    @Delete
    suspend fun deleteLandmark(landmark: Landmark)

    @Query("SELECT * FROM landmarks WHERE id = :id")
    suspend fun getLandmarkById(id: String): Landmark?

    @Query("SELECT * FROM landmarks WHERE placeId = :placeId")
    fun getLandmarksForPlace(placeId: String): Flow<List<Landmark>>

    /**
     * Retrieves all landmarks associated with a place, ordered by highest baseline confirmation count ([Landmark.visitsConfirmed]).
     *
     * @param placeId Target place identifier.
     * @return Reactive Flow of Landmark list ordered by visitsConfirmed descending.
     */
    @Query("SELECT * FROM landmarks WHERE placeId = :placeId ORDER BY visitsConfirmed DESC")
    fun getLandmarksForPlaceOrderedByVisits(placeId: String): Flow<List<Landmark>>

    /**
     * Retrieves landmarks within a specified 3D Euclidean radius of a focal point `(centerX, centerY, centerZ)`.
     *
     * Used by the spatial diffing engine to check for expected baseline objects during scanning.
     *
     * @param placeId Target place identifier.
     * @param centerX Focal point X coordinate (meters).
     * @param centerY Focal point Y coordinate (meters).
     * @param centerZ Focal point Z coordinate (meters).
     * @param radius Maximum distance radius (meters) from focal point.
     * @return List of matching landmarks within radius.
     */
    @Query("""
        SELECT * FROM landmarks 
        WHERE placeId = :placeId 
        AND ((positionX - :centerX) * (positionX - :centerX) + 
             (positionY - :centerY) * (positionY - :centerY) + 
             (positionZ - :centerZ) * (positionZ - :centerZ)) <= (:radius * :radius)
    """)
    suspend fun getLandmarksNearPoint(
        placeId: String,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        radius: Float
    ): List<Landmark>

    /**
     * Increments the confirmation count ([Landmark.visitsConfirmed]) and updates the [Landmark.lastSeenAt] timestamp
     * when an existing landmark is re-observed during a new scanning session.
     *
     * @param landmarkId Target landmark identifier.
     * @param lastSeenAt New epoch timestamp in milliseconds.
     */
    @Query("UPDATE landmarks SET visitsConfirmed = visitsConfirmed + 1, lastSeenAt = :lastSeenAt WHERE id = :landmarkId")
    suspend fun incrementVisitsAndSeen(landmarkId: String, lastSeenAt: Long)
}

/**
 * Data Access Object for [WalkCorridor] entities.
 */
@Dao
interface WalkCorridorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWalkCorridor(corridor: WalkCorridor)

    @Update
    suspend fun updateWalkCorridor(corridor: WalkCorridor)

    @Delete
    suspend fun deleteWalkCorridor(corridor: WalkCorridor)

    @Query("SELECT * FROM walk_corridors WHERE id = :id")
    suspend fun getWalkCorridorById(id: String): WalkCorridor?

    @Query("SELECT * FROM walk_corridors WHERE placeId = :placeId")
    fun getWalkCorridorsForPlace(placeId: String): Flow<List<WalkCorridor>>
}

/**
 * Data Access Object for [ChangeEvent] entities.
 */
@Dao
interface ChangeEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChangeEvent(event: ChangeEvent)

    @Update
    suspend fun updateChangeEvent(event: ChangeEvent)

    @Delete
    suspend fun deleteChangeEvent(event: ChangeEvent)

    @Query("SELECT * FROM change_events WHERE id = :id")
    suspend fun getChangeEventById(id: String): ChangeEvent?

    /**
     * Retrieves all unresolved change events for a place (where [ChangeEvent.userFeedback] is NULL).
     *
     * @param placeId Target place identifier.
     * @return Reactive Flow of unresolved ChangeEvent list ordered by detectedAt descending.
     */
    @Query("SELECT * FROM change_events WHERE placeId = :placeId AND userFeedback IS NULL ORDER BY detectedAt DESC")
    fun getUnresolvedChangeEventsForPlace(placeId: String): Flow<List<ChangeEvent>>

    /**
     * Retrieves recent change events for a place detected after a given timestamp.
     *
     * @param placeId Target place identifier.
     * @param sinceTimestamp Lower bound epoch timestamp in milliseconds.
     * @return Reactive Flow of recent ChangeEvents ordered by detectedAt descending.
     */
    @Query("SELECT * FROM change_events WHERE placeId = :placeId AND detectedAt >= :sinceTimestamp ORDER BY detectedAt DESC")
    fun getRecentChangeEventsForPlace(placeId: String, sinceTimestamp: Long): Flow<List<ChangeEvent>>

    /**
     * Updates user feedback on a change event for the personalization learning loop.
     *
     * @param eventId Target change event identifier.
     * @param feedback User feedback value ([UserFeedback.USEFUL] or [UserFeedback.NOT_USEFUL]).
     */
    @Query("UPDATE change_events SET userFeedback = :feedback WHERE id = :eventId")
    suspend fun updateUserFeedback(eventId: String, feedback: UserFeedback)
}
