package com.spatialmemory.app.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Data class representing a 2D object detection result from the TFLite vision model.
 *
 * @property label Class label string (e.g., "chair", "couch", "backpack").
 * @property confidence Detection confidence score ranging from 0.0 to 1.0.
 * @property boundingBox Normalized 2D bounding box [RectF] relative to input image dimensions (0.0 to 1.0).
 * @property classId COCO dataset integer class ID (0 to 79).
 */
data class Detection(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val classId: Int
)

/**
 * Runs object detection using a quantized YOLOv8 TFLite model on camera frames.
 *
 * Configures hardware acceleration via NNAPI delegate when supported by the host device,
 * falling back gracefully to 4-thread CPU inference.
 *
 * @param context Application context for asset loading.
 */
class ObjectDetector(private val context: Context) : AutoCloseable {

    companion object {
        const val MODEL_FILENAME = "yolov8n_int8.tflite"
        const val INPUT_SIZE = 640 // Standard YOLOv8 input tensor width & height
        const val NUM_CLASSES = 80
        const val IOU_THRESHOLD = 0.45f

        /** Complete COCO dataset 80 class labels. */
        val COCO_LABELS = arrayOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
            "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
            "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        )

        /**
         * Curated subset of indoor and pathway obstacle labels relevant to visually impaired navigation.
         *
         * Irrelevant COCO classes (e.g. "tie", "toothbrush", "donut") are filtered out before 3D spatial mapping.
         */
        val RELEVANT_LABELS = setOf(
            "chair", "couch", "bench", "dining table", "bed", "toilet",
            "backpack", "suitcase", "handbag", "umbrella",
            "bicycle", "motorcycle", "car", "potted plant", "person", "tv", "laptop"
        )
    }

    private var interpreter: Interpreter? = null
    private var nnApiDelegate: NnApiDelegate? = null

    init {
        initInterpreter()
    }

    private fun initInterpreter() {
        val options = Interpreter.Options()
        try {
            // Attempt hardware acceleration via NNAPI delegate
            nnApiDelegate = NnApiDelegate()
            options.addDelegate(nnApiDelegate)
        } catch (e: Exception) {
            // NNAPI initialization failed or unavailable on this Android build; fallback to multithreaded CPU
            nnApiDelegate = null
            options.setNumThreads(4)
        }

        try {
            val modelBuffer = loadModelFile(context, MODEL_FILENAME)
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            // Handle model file loading exception in fallback mode
            interpreter = null
        }
    }

    private fun loadModelFile(context: Context, filename: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Executes object detection on an input [Bitmap].
     *
     * Performs image resizing, input normalization, tensor inference, and Non-Maximum Suppression (NMS)
     * on [Dispatchers.Default].
     *
     * @param bitmap Camera frame bitmap.
     * @param confidenceThreshold Minimum detection confidence score (default 0.40f).
     * @return List of filtered 2D [Detection] objects.
     */
    suspend fun detect(bitmap: Bitmap, confidenceThreshold: Float = 0.4f): List<Detection> = withContext(Dispatchers.Default) {
        val tflite = interpreter ?: return@withContext emptyList()

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val imgData = convertBitmapToByteBuffer(resizedBitmap)

        // YOLOv8 output tensor layout: [1, 84, 8400] -> 4 bbox coords + 80 class scores across 8400 candidate anchors
        val outputTensor = Array(1) { Array(84) { FloatArray(8400) } }
        tflite.run(imgData, outputTensor)

        val detections = parseYoloV8Output(outputTensor[0], confidenceThreshold, bitmap.width, bitmap.height)
        applyNms(detections, IOU_THRESHOLD)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val valPixel = intValues[pixel++]
                // Normalize RGB pixels to [0.0, 1.0]
                byteBuffer.putFloat(((valPixel shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((valPixel shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((valPixel and 0xFF) / 255.0f)
            }
        }
        return byteBuffer
    }

    private fun parseYoloV8Output(
        output: Array<FloatArray>,
        confidenceThreshold: Float,
        imageWidth: Int,
        imageHeight: Int
    ): MutableList<Detection> {
        val detections = mutableListOf<Detection>()
        val numAnchors = 8400

        for (i in 0 until numAnchors) {
            var maxScore = 0.0f
            var maxClassId = -1

            for (c in 0 until NUM_CLASSES) {
                val score = output[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    maxClassId = c
                }
            }

            if (maxScore >= confidenceThreshold && maxClassId in COCO_LABELS.indices) {
                val cx = output[0][i] / INPUT_SIZE
                val cy = output[1][i] / INPUT_SIZE
                val w = output[2][i] / INPUT_SIZE
                val h = output[3][i] / INPUT_SIZE

                val left = (cx - w / 2.0f).coerceIn(0.0f, 1.0f)
                val top = (cy - h / 2.0f).coerceIn(0.0f, 1.0f)
                val right = (cx + w / 2.0f).coerceIn(0.0f, 1.0f)
                val bottom = (cy + h / 2.0f).coerceIn(0.0f, 1.0f)

                val label = COCO_LABELS[maxClassId]
                val bbox = RectF(left * imageWidth, top * imageHeight, right * imageWidth, bottom * imageHeight)
                detections.add(Detection(label, maxScore, bbox, maxClassId))
            }
        }
        return detections
    }

    private fun applyNms(detections: MutableList<Detection>, iouThreshold: Float): List<Detection> {
        detections.sortByDescending { it.confidence }
        val result = mutableListOf<Detection>()

        while (detections.isNotEmpty()) {
            val best = detections.removeAt(0)
            result.add(best)

            val iterator = detections.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (next.classId == best.classId && calculateIoU(best.boundingBox, next.boundingBox) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return result
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val intersectionLeft = maxOf(a.left, b.left)
        val intersectionTop = maxOf(a.top, b.top)
        val intersectionRight = minOf(a.right, b.right)
        val intersectionBottom = minOf(a.bottom, b.bottom)

        val intersectionArea = maxOf(0.0f, intersectionRight - intersectionLeft) * maxOf(0.0f, intersectionBottom - intersectionTop)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)

        val unionArea = areaA + areaB - intersectionArea
        return if (unionArea <= 0.0f) 0.0f else intersectionArea / unionArea
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        nnApiDelegate?.close()
        nnApiDelegate = null
    }
}
