package com.spatialmemory.app.detection

/**
 * Encapsulates a 2D object detection combined with its computed 3D position in the AR session world frame.
 *
 * @property detection Original 2D [Detection] containing label, confidence score, and 2D bounding box.
 * @property worldPosition 3-element FloatArray `[x, y, z]` specifying object position in meters within the ARCore world coordinate frame.
 * @property estimatedHeightMeters Estimated physical height of the object in meters (derived from bounding box projection).
 * @property distanceFromCameraMeters Distance in meters from the camera sensor to the object's 3D ground contact point.
 */
data class SpatialDetection(
    val detection: Detection,
    val worldPosition: FloatArray,
    val estimatedHeightMeters: Float,
    val distanceFromCameraMeters: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpatialDetection

        if (detection != other.detection) return false
        if (!worldPosition.contentEquals(other.worldPosition)) return false
        if (estimatedHeightMeters != other.estimatedHeightMeters) return false
        if (distanceFromCameraMeters != other.distanceFromCameraMeters) return false

        return true
    }

    override fun hashCode(): Int {
        var result = detection.hashCode()
        result = 31 * result + worldPosition.contentHashCode()
        result = 31 * result + estimatedHeightMeters.hashCode()
        result = 31 * result + distanceFromCameraMeters.hashCode()
        return result
    }
}
