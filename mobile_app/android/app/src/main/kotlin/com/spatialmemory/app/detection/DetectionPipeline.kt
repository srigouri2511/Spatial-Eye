package com.spatialmemory.app.detection

import android.content.Context
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * End-to-end vision and spatial detection pipeline for the Spatial Memory Assistant.
 *
 * Coordinates raw ARCore frame acquisition, YUV-to-Bitmap conversion, TFLite object detection,
 * indoor obstacle label filtering, and 3D AR world coordinate mapping.
 *
 * ### Frame Throttling Rationale:
 * Executing TFLite model inference and 3D raycasting on every incoming camera frame (30–60 FPS) will rapidly
 * overheat device hardware, trigger thermal throttling, and starve the ARCore SLAM tracking loop.
 * **Caller Recommendation**: Callers should implement a frame-skip counter in their render loop, invoking
 * [processFrame] approximately every 5th to 10th camera frame (~3 to 6 Hz). This cadence provides timely obstacle
 * detection for human walking speeds while preserving battery life and device thermal headroom.
 *
 * @param context Application context.
 * @param objectDetector [ObjectDetector] instance.
 * @param mapper [DetectionToWorldMapper] instance.
 */
class DetectionPipeline(
    private val context: Context,
    private val objectDetector: ObjectDetector = ObjectDetector(context),
    private val mapper: DetectionToWorldMapper = DetectionToWorldMapper()
) : AutoCloseable {

    /**
     * Processes a single ARCore camera [Frame], returning a list of filtered 3D [SpatialDetection] objects.
     *
     * Runs image conversion, model inference, and 3D spatial mapping asynchronously on [Dispatchers.Default].
     *
     * @param frame Active render tick [Frame] acquired from ARCore session.
     * @return List of 3D spatial detections mapped to world coordinates.
     */
    suspend fun processFrame(frame: Frame): List<SpatialDetection> = withContext(Dispatchers.Default) {
        val image = try {
            frame.acquireCameraImage()
        } catch (e: NotYetAvailableException) {
            return@withContext emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        try {
            // Step 1: Convert YUV_420_888 camera image to ARGB Bitmap
            val bitmap = FrameConverter.yuvToBitmap(image)

            // Step 2: Run TFLite object detection
            val rawDetections = objectDetector.detect(bitmap, confidenceThreshold = 0.40f)

            // Step 3: Filter for indoor/pathway obstacle labels
            val relevantDetections = rawDetections.filter { detection ->
                ObjectDetector.RELEVANT_LABELS.contains(detection.label.lowercase())
            }

            // Step 4: Map surviving 2D detections to 3D AR world space
            val spatialDetections = mutableListOf<SpatialDetection>()
            for (detection in relevantDetections) {
                val spatialDetection = mapper.mapToWorldSpace(detection, frame)
                if (spatialDetection != null) {
                    spatialDetections.add(spatialDetection)
                }
            }

            spatialDetections
        } catch (e: Exception) {
            emptyList()
        } finally {
            image.close() // Crucial: Always release raw ARCore camera image resource immediately
        }
    }

    override fun close() {
        objectDetector.close()
    }
}
