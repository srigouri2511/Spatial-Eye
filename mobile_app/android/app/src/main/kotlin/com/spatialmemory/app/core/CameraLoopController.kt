package com.spatialmemory.app.core

import android.content.Context
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.spatialmemory.app.alerting.AlertCoordinator
import com.spatialmemory.app.alerting.AlertManager
import com.spatialmemory.app.ar.ArSessionManager
import com.spatialmemory.app.ar.RelocalizationState
import com.spatialmemory.app.ar.WorldMapPersistence
import com.spatialmemory.app.data.Place
import com.spatialmemory.app.data.SpatialMemoryDatabase
import com.spatialmemory.app.detection.DetectionPipeline
import com.spatialmemory.app.diffing.ChangeDetectionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2

/**
 * Manages the background camera processing loop for the Spatial Memory Assistant.
 *
 * ### Non-GL Camera Loop Rationale:
 * Because Spatial Memory Assistant communicates via auditory speech, stereo audio tones, and haptic vibrations
 * rather than rendering visual 3D graphics over the video feed, custom OpenGL rendering is omitted. The processing loop
 * consumes 6DoF camera poses and feature frame data directly from ARCore `session.update()` via a coroutine loop.
 *
 * ### Frame Throttling & Relocalization Gate:
 * 1. **Throttling**: Processes 3D detection and diffing every 8th camera frame (~4 Hz at 30 FPS) to conserve battery
 *    and prevent device thermal throttling.
 * 2. **Relocalization Safety Gate**: Before executing diffing, verifies that ARCore has achieved [RelocalizationState.Localized]
 *    against the active place map. If unlocalized, diffing is paused and a one-time auditory prompt is spoken.
 *
 * @param context Application context.
 * @param database [SpatialMemoryDatabase] instance.
 * @param arSessionManager [ArSessionManager] instance.
 * @param detectionPipeline [DetectionPipeline] instance.
 * @param diffingEngine [ChangeDetectionEngine] instance.
 * @param alertCoordinator [AlertCoordinator] instance.
 * @param alertManager [AlertManager] instance.
 * @param persistence [WorldMapPersistence] instance.
 */
class CameraLoopController(
    private val context: Context,
    private val database: SpatialMemoryDatabase,
    private val arSessionManager: ArSessionManager,
    private val detectionPipeline: DetectionPipeline,
    private val diffingEngine: ChangeDetectionEngine,
    private val alertCoordinator: AlertCoordinator,
    private val alertManager: AlertManager,
    private val persistence: WorldMapPersistence = WorldMapPersistence()
) {

    companion object {
        /** Frame skip counter interval. Process 1 frame every 8 camera frames (~4 Hz). */
        const val FRAME_SKIP_INTERVAL: Int = 8
    }

    var currentPlace: Place? = null
    private var loopJob: Job? = null
    private var frameCounter: Int = 0
    private var hasAnnouncedRelocalizing: Boolean = false

    /**
     * Starts the camera processing loop on the provided ARCore [Session].
     *
     * @param session Active ARCore [Session].
     * @param scope [CoroutineScope] managing loop execution.
     */
    fun startLoop(session: Session, scope: CoroutineScope) {
        stopLoop()
        hasAnnouncedRelocalizing = false

        loopJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    val frame = withContext(Dispatchers.Main) {
                        arSessionManager.currentFrame(session)
                    }

                    if (frame != null) {
                        frameCounter++
                        if (frameCounter % FRAME_SKIP_INTERVAL == 0) {
                            processSingleFrame(session, frame)
                        }
                    }
                    delay(33L) // ~30 FPS polling tick
                } catch (e: Exception) {
                    delay(100L)
                }
            }
        }
    }

    private suspend fun processSingleFrame(session: Session, frame: Frame) {
        val place = currentPlace ?: return

        // Step 1: Check ARCore Relocalization State
        val relocState = persistence.checkRelocalizationState(session, frame, place.id)
        if (relocState !is RelocalizationState.Localized) {
            if (!hasAnnouncedRelocalizing) {
                hasAnnouncedRelocalizing = true
                withContext(Dispatchers.Main) {
                    alertManager.handleChanges(
                        changes = emptyList(),
                        userPosition = floatArrayOf(0f, 0f, 0f),
                        userHeading = 0f
                    )
                }
            }
            return
        }

        hasAnnouncedRelocalizing = false

        // Step 2: Fetch stored landmarks and walking corridors from Room DB
        val landmarks = database.landmarkDao()
            .getLandmarksForPlace(place.id)
            .firstOrNull() ?: emptyList()

        val corridors = database.walkCorridorDao()
            .getWalkCorridorsForPlace(place.id)
            .firstOrNull() ?: emptyList()

        // Step 3: Run TFLite object detection & 3D world mapping
        val liveSpatialDetections = detectionPipeline.processFrame(frame)

        // Step 4: Run spatial diffing engine
        val detectedChanges = diffingEngine.diff(place, liveSpatialDetections, landmarks, corridors)

        // Step 5: Compute 2D camera position & facing heading
        val pose = arSessionManager.cameraPose(frame) ?: return
        val userPosition = floatArrayOf(pose.tx(), pose.ty(), pose.tz())

        // Calculate camera facing heading in radians from quaternion rotation (rotation around Y-axis)
        val qy = pose.qy()
        val qw = pose.qw()
        val userHeading = (2.0 * atan2(qy.toDouble(), qw.toDouble())).toFloat()

        // Step 6: Forward scored changes to AlertCoordinator for debouncing & delivery
        alertCoordinator.onFrameProcessed(detectedChanges, userPosition, userHeading)
    }

    /**
     * Pauses the camera processing loop.
     */
    fun pauseLoop() {
        stopLoop()
    }

    /**
     * Stops and cancels the camera processing loop.
     */
    fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        frameCounter = 0
    }
}
