package com.spatialmemory.app.ar

import android.content.Context
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.spatialmemory.app.data.Place
import com.spatialmemory.app.data.SpatialMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Data class representing the real-time scanning/mapping progress during room setup.
 *
 * @property planesDetected Number of distinct tracked horizontal and vertical planes.
 * @property areaCoveredSqM Estimated total surface area (in square meters) of mapped planes.
 * @property isReadyToSave True when both minimum plane count and minimum area coverage thresholds are satisfied.
 */
data class MappingProgress(
    val planesDetected: Int = 0,
    val areaCoveredSqM: Float = 0.0f,
    val isReadyToSave: Boolean = false
)

/**
 * Controls the room scanning/mapping workflow ("scan this room" flow) when creating a new [Place].
 *
 * Tracks plane detection density and surface coverage area during scanning, exposing progress via a [Flow].
 * Once minimum SLAM mapping criteria are satisfied, saves the ARCore world map keyframes to disk and persists
 * the new [Place] record into the Room database.
 *
 * @param context Application context.
 * @param database [SpatialMemoryDatabase] instance for persisting the created [Place].
 * @param persistence [WorldMapPersistence] handler for saving ARCore spatial map manifests.
 */
class MappingModeController(
    private val context: Context,
    private val database: SpatialMemoryDatabase,
    private val persistence: WorldMapPersistence = WorldMapPersistence()
) {

    companion object {
        /** Minimum number of distinct tracked planes required before allowing map save. */
        const val MIN_PLANES: Int = 3

        /** Minimum cumulative surface coverage area (in square meters) required before allowing map save. */
        const val MIN_AREA_SQ_METERS: Float = 5.0f
    }

    private var currentPlaceId: String? = null
    private var currentDisplayName: String? = null
    private var mappingStartTime: Long = 0L

    private val _progressFlow = MutableStateFlow(MappingProgress())

    /**
     * Exposes real-time room scanning progress updates.
     */
    fun mappingProgressFlow(): Flow<MappingProgress> = _progressFlow.asStateFlow()

    /**
     * Initiates a new room scanning session for a target place.
     *
     * @param placeId Unique place string identifier (e.g. "home_living_room").
     * @param displayName Human-readable place name (e.g. "Living Room").
     */
    fun startMapping(placeId: String, displayName: String) {
        currentPlaceId = placeId
        currentDisplayName = displayName
        mappingStartTime = System.currentTimeMillis()
        _progressFlow.value = MappingProgress()
    }

    /**
     * Processes an ARCore render tick frame, updating plane detection progress and area coverage metrics.
     *
     * MUST be invoked on each render frame during mapping mode.
     *
     * @param session Active ARCore [Session].
     * @param frame Current ARCore render [Frame].
     * @return Updated [MappingProgress] snapshot.
     */
    fun processFrame(session: Session, frame: Frame): MappingProgress {
        val allPlanes = session.getAllTrackables(Plane::class.java)
        val trackedPlanes = allPlanes.filter { it.trackingState == TrackingState.TRACKING }

        var totalAreaSqM = 0.0f
        for (plane in trackedPlanes) {
            // Estimate plane surface area using bounding dimensions (extentX * extentZ)
            // TODO: In production, substitute with exact convex polygon hull area calculation for complex rooms
            val planeArea = plane.extentX * plane.extentZ
            totalAreaSqM += planeArea
        }

        val planesCount = trackedPlanes.size
        // TODO: Tune threshold values based on target device camera FoV and sensor fusion accuracy
        val ready = planesCount >= MIN_PLANES && totalAreaSqM >= MIN_AREA_SQ_METERS

        val newProgress = MappingProgress(
            planesDetected = planesCount,
            areaCoveredSqM = totalAreaSqM,
            isReadyToSave = ready
        )

        _progressFlow.value = newProgress
        return newProgress
    }

    /**
     * Finalizes room mapping by saving the ARCore world map to file storage and inserting the new [Place] row
     * into the Room database.
     *
     * @param session Active ARCore [Session].
     * @return [Result] containing the saved [Place] entity on success, or a failure exception.
     */
    suspend fun finishMapping(session: Session): Result<Place> = withContext(Dispatchers.IO) {
        val placeId = currentPlaceId
            ?: return@withContext Result.failure(IllegalStateException("Mapping session not started. Call startMapping() first."))
        val displayName = currentDisplayName ?: placeId

        val now = System.currentTimeMillis()
        val tempPlace = Place(
            id = placeId,
            displayName = displayName,
            lastUpdated = now,
            arWorldMapFilePath = "", // Will be populated by saveWorldMap
            createdAt = mappingStartTime.takeIf { it > 0 } ?: now
        )

        // Step 1: Save ARCore world map manifest to disk
        val saveResult = persistence.saveWorldMap(context, session, tempPlace)
        if (saveResult.isFailure) {
            return@withContext Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to save AR world map file."))
        }

        val mapFilePath = saveResult.getOrThrow()

        // Step 2: Construct final Place entity with valid file path
        val finalPlace = tempPlace.copy(
            arWorldMapFilePath = mapFilePath,
            lastUpdated = System.currentTimeMillis()
        )

        // Step 3: Insert Place into Room local database
        return@withContext try {
            database.placeDao().insertPlace(finalPlace)
            Result.success(finalPlace)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
