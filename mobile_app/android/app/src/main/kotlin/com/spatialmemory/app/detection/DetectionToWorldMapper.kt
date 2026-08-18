package com.spatialmemory.app.detection

import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlin.math.sqrt

/**
 * Projects 2D image-space object detections into 3D world coordinates within the ARCore spatial frame.
 */
class DetectionToWorldMapper {

    /**
     * Maps a 2D [Detection] to 3D world coordinates [SpatialDetection].
     *
     * ### Center-Bottom Raycasting Rationale:
     * We perform hit testing at the **center-bottom point** of the 2D bounding box (`centerX`, `bottomY`)
     * rather than the visual centroid. The bottom edge of the bounding box represents the object's contact point
     * with the floor plane. For visually impaired navigation and walking corridor obstacle diffing, the ground contact
     * point is what determines whether an object obstructs a walking path or presents a trip hazard.
     *
     * ### Depth API vs Plane Hit Test Trade-off:
     * If the device supports the ARCore Depth API (`frame.acquireDepthImage16Bits()`), pixel-accurate depth measurements
     * are extracted from raw depth maps. If Depth API is uninitialized or unsupported on the device, the mapper falls back
     * to ARCore plane hit testing ([Frame.hitTest]).
     *
     * @param detection 2D detection result.
     * @param frame Current ARCore render [Frame].
     * @return [SpatialDetection] or null if the raycast fails to intersect a tracked AR surface.
     */
    fun mapToWorldSpace(detection: Detection, frame: Frame): SpatialDetection? {
        val bbox = detection.boundingBox

        // Calculate center-bottom pixel coordinates (floor contact point)
        val centerX = (bbox.left + bbox.right) / 2.0f
        val bottomY = bbox.bottom

        // Step 1: Attempt hit test against ARCore tracked planes
        val hitResults = frame.hitTest(centerX, bottomY)
        val validHit = hitResults.firstOrNull { hit ->
            val trackable = hit.trackable
            hit.isHitInPolygon && trackable is Plane && trackable.trackingState == TrackingState.TRACKING
        } ?: hitResults.firstOrNull()

        if (validHit == null) return null

        val pose = validHit.pose
        val worldPosition = floatArrayOf(pose.tx(), pose.ty(), pose.tz())

        // Calculate 3D camera distance
        val cameraPose = frame.camera.pose
        val dx = pose.tx() - cameraPose.tx()
        val dy = pose.ty() - cameraPose.ty()
        val dz = pose.tz() - cameraPose.tz()
        var distanceMeters = sqrt(dx * dx + dy * dy + dz * dz)

        // Step 2: Query ARCore Depth API if available for distance refinement
        try {
            val depthImage = frame.acquireDepthImage16Bits()
            val pixelX = centerX.toInt().coerceIn(0, depthImage.width - 1)
            val pixelY = bottomY.toInt().coerceIn(0, depthImage.height - 1)

            val planes = depthImage.planes
            val buffer = planes[0].buffer
            val stride = planes[0].pixelStride
            val rowStride = planes[0].rowStride

            val index = pixelY * rowStride + pixelX * stride
            if (index < buffer.capacity() - 1) {
                // 16-bit depth in millimeters -> convert to meters
                val depthMm = (buffer.get(index).toInt() and 0xFF) or ((buffer.get(index + 1).toInt() and 0xFF) shl 8)
                if (depthMm > 0) {
                    val depthMeters = depthMm / 1000.0f
                    // Use depth API distance if within valid range (0.2m to 5.0m)
                    if (depthMeters in 0.2f..5.0f) {
                        distanceMeters = depthMeters
                    }
                }
            }
            depthImage.close()
        } catch (e: NotYetAvailableException) {
            // Depth map image not yet available on this frame; use hit test plane distance fallback
        } catch (e: Exception) {
            // Depth API unsupported or exception thrown; use hit test plane distance fallback
        }

        // Estimate object physical height based on 2D box height and camera distance
        val bboxHeightPixels = (bbox.bottom - bbox.top)
        val estimatedHeightMeters = (bboxHeightPixels * distanceMeters / 500.0f).coerceIn(0.1f, 2.5f)

        return SpatialDetection(
            detection = detection,
            worldPosition = worldPosition,
            estimatedHeightMeters = estimatedHeightMeters,
            distanceFromCameraMeters = distanceMeters
        )
    }
}
