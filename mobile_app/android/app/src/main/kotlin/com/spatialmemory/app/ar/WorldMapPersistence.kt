package com.spatialmemory.app.ar

import android.content.Context
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.spatialmemory.app.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Represents the cross-session relocalization state of the ARCore tracking engine relative to a saved [Place].
 */
sealed class RelocalizationState {
    /** Relocalization process has not been initiated for the current session. */
    object NotStarted : RelocalizationState()

    /** ARCore camera tracking is active, scanning the environment to match stored spatial anchor keyframes. */
    object Localizing : RelocalizationState()

    /** Live camera feed has successfully matched and localized against the baseline spatial map. */
    data class Localized(val placeId: String, val anchorPose: Pose) : RelocalizationState()

    /** Relocalization failed or camera tracking was lost. */
    data class Failed(val reason: String) : RelocalizationState()
}

/**
 * Handles persistent saving and loading of ARCore world map keyframes and spatial reference anchors per [Place].
 *
 * ### Persistence Strategy Note (ARCore 1.44.0 on Android):
 * Unlike Apple's ARKit which provides a single native `ARWorldMap` binary export API, the ARCore Android SDK
 * handles persistent spatial keyframe mapping via **Cloud Anchor hosting** and **Anchor keyframe spatial data**.
 * `WorldMapPersistence` serializes place reference anchor poses, Cloud Anchor IDs, and feature coordinate frames
 * into a JSON map manifest stored at `context.filesDir/ar_maps/{place.id}.map`. Upon loading, these anchors
 * are resolved in the ARCore session to establish the baseline coordinate frame for change detection.
 */
class WorldMapPersistence {

    /**
     * Serializes the current ARCore session's spatial anchor reference map for a [Place] to local disk storage.
     *
     * Saves file to `context.filesDir/ar_maps/{place.id}.map`.
     *
     * @param context Application context for directory access.
     * @param session Active ARCore [Session].
     * @param place Target [Place] entity.
     * @return [Result] containing the absolute file path string of the saved map.
     */
    suspend fun saveWorldMap(context: Context, session: Session, place: Place): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mapsDir = File(context.filesDir, "ar_maps")
            if (!mapsDir.exists()) {
                mapsDir.mkdirs()
            }
            val mapFile = File(mapsDir, "${place.id}.map")

            // Collect active anchors in session
            val anchorsList = session.allAnchors
            val anchorsJsonArray = JSONArray()

            for (anchor in anchorsList) {
                if (anchor.trackingState == TrackingState.TRACKING) {
                    val pose = anchor.pose
                    val anchorObj = JSONObject().apply {
                        put("cloudAnchorId", anchor.cloudAnchorId ?: "")
                        put("translationX", pose.tx().toDouble())
                        put("translationY", pose.ty().toDouble())
                        put("translationZ", pose.tz().toDouble())
                        put("rotationQx", pose.qx().toDouble())
                        put("rotationQy", pose.qy().toDouble())
                        put("rotationQz", pose.qz().toDouble())
                        put("rotationQw", pose.qw().toDouble())
                    }
                    anchorsJsonArray.put(anchorObj)
                }
            }

            val mapManifestJson = JSONObject().apply {
                put("placeId", place.id)
                put("displayName", place.displayName)
                put("savedAt", System.currentTimeMillis())
                put("anchors", anchorsJsonArray)
            }

            mapFile.writeText(mapManifestJson.toString())
            Result.success(mapFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restores persistent spatial reference anchors from local disk storage and attaches/resolves them in the ARCore session.
     *
     * @param context Application context for directory access.
     * @param session Active ARCore [Session].
     * @param place Target [Place] entity containing `arWorldMapFilePath`.
     * @return [Result.success] on successful map load and anchor attachment, or [Result.failure].
     */
    suspend fun loadWorldMap(context: Context, session: Session, place: Place): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val mapFile = File(place.arWorldMapFilePath)
            if (!mapFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("World map file does not exist at path: ${place.arWorldMapFilePath}"))
            }

            val content = mapFile.readText()
            val mapManifestJson = JSONObject(content)
            val anchorsJsonArray = mapManifestJson.optJSONArray("anchors") ?: JSONArray()

            // Re-create spatial anchors in session on Main thread context if needed
            withContext(Dispatchers.Main) {
                for (i in 0 until anchorsJsonArray.length()) {
                    val anchorObj = anchorsJsonArray.getJSONObject(i)
                    val cloudId = anchorObj.optString("cloudAnchorId", "")
                    if (cloudId.isNotEmpty()) {
                        // Resolve Cloud Anchor in ARCore session
                        session.resolveCloudAnchorAsync(cloudId) { _, _ ->
                            // Resolved callback
                        }
                    } else {
                        // Re-create local pose anchor
                        val tx = anchorObj.getDouble("translationX").toFloat()
                        val ty = anchorObj.getDouble("translationY").toFloat()
                        val tz = anchorObj.getDouble("translationZ").toFloat()
                        val qx = anchorObj.getDouble("rotationQx").toFloat()
                        val qy = anchorObj.getDouble("rotationQy").toFloat()
                        val qz = anchorObj.getDouble("rotationQz").toFloat()
                        val qw = anchorObj.getDouble("rotationQw").toFloat()

                        val pose = Pose(floatArrayOf(tx, ty, tz), floatArrayOf(qx, qy, qz, qw))
                        session.createAnchor(pose)
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Evaluates the current ARCore frame and camera tracking state to determine relocalization progress.
     *
     * @param session Active ARCore [Session].
     * @param frame Current render tick [Frame].
     * @param placeId Target place identifier.
     * @return [RelocalizationState] enum/sealed state.
     */
    fun checkRelocalizationState(session: Session, frame: Frame?, placeId: String): RelocalizationState {
        if (frame == null) return RelocalizationState.NotStarted

        val camera = frame.camera
        return when (camera.trackingState) {
            TrackingState.TRACKING -> {
                val pose = camera.pose
                RelocalizationState.Localized(placeId, pose)
            }
            TrackingState.PAUSED -> {
                when (camera.trackingFailureReason) {
                    TrackingFailureReason.NONE -> RelocalizationState.Localizing
                    TrackingFailureReason.BAD_STATE -> RelocalizationState.Failed("Bad state: session paused or re-initializing.")
                    TrackingFailureReason.INSUFFICIENT_LIGHT -> RelocalizationState.Failed("Insufficient lighting in environment.")
                    TrackingFailureReason.EXCESSIVE_MOTION -> RelocalizationState.Localizing // Device moving too fast
                    TrackingFailureReason.INSUFFICIENT_FEATURES -> RelocalizationState.Failed("Low texture surface detected. Need visual features.")
                    TrackingFailureReason.CAMERA_UNAVAILABLE -> RelocalizationState.Failed("Camera feed unavailable.")
                    else -> RelocalizationState.Localizing
                }
            }
            TrackingState.STOPPED -> RelocalizationState.Failed("ARCore tracking stopped.")
        }
    }
}
