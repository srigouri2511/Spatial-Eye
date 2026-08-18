package com.spatialmemory.app.detection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream

/**
 * Utility for converting raw Android YUV_420_888 camera frames into ARGB [Bitmap] objects.
 *
 * ### Architectural Bridge Note:
 * ARCore's `Frame.acquireCameraImage()` returns camera frames in raw [ImageFormat.YUV_420_888] format.
 * `FrameConverter` acts as the vital bridge between [com.spatialmemory.app.ar.ArSessionManager.currentFrame]
 * and [ObjectDetector.detect], transforming raw YUV image planes into an RGB [Bitmap] acceptable by TFLite.
 */
object FrameConverter {

    /**
     * Converts an [ImageFormat.YUV_420_888] camera frame [Image] into a standard RGB [Bitmap].
     *
     * @param image Raw camera frame image acquired from ARCore frame.
     * @return Converted ARGB_8888 [Bitmap].
     */
    fun yuvToBitmap(image: Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V plane byte copying into NV21 format
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)

        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
