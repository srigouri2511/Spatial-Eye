package com.spatialmemory.app.alerting

import com.spatialmemory.app.data.ChangeEventType
import com.spatialmemory.app.data.Landmark
import com.spatialmemory.app.data.PermanenceClass
import com.spatialmemory.app.diffing.AlertThresholds
import com.spatialmemory.app.diffing.DetectedChange
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyFloat
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * Unit test suite for [AlertCoordinator] rate-limiting / debouncing logic.
 */
class AlertCoordinatorTest {

    private lateinit var mockAlertManager: AlertManager
    private lateinit var mockFeedbackListener: FeedbackListener
    private lateinit var alertCoordinator: AlertCoordinator

    @Before
    fun setUp() {
        mockAlertManager = mock(AlertManager::class.java)
        mockFeedbackListener = mock(FeedbackListener::class.java)

        @Suppress("UNCHECKED_CAST")
        alertCoordinator = AlertCoordinator(
            context = null as Nothing?,
            database = null as Nothing?,
            alertManager = mockAlertManager,
            feedbackListener = mockFeedbackListener
        )
    }

    @Test
    fun should_suppressDuplicateAlert_when_sameObjectProcessedWithinDebounceWindow() {
        val landmark = Landmark(
            id = "lm_chair_1",
            placeId = "place_1",
            label = "chair",
            positionX = 0f, positionY = 0f, positionZ = 0f,
            permanenceClass = PermanenceClass.DYNAMIC,
            visitsConfirmed = 3,
            lastSeenAt = 1000L, firstSeenAt = 1000L,
            boundingBoxHeight = 0.8f, detectionConfidence = 0.9f
        )

        val change = DetectedChange(
            eventType = ChangeEventType.OBJECT_MOVED,
            landmark = landmark,
            liveDetection = null,
            severityScore = 0.70f,
            distanceFromCorridorMeters = 0.2f,
            explanation = "Chair moved into your path."
        )

        runBlocking {
            `when`(mockAlertManager.handleChanges(any(), any(), anyFloat())).thenReturn(listOf(change))

            // First frame processing tick
            alertCoordinator.onFrameProcessed(listOf(change), floatArrayOf(0f, 0f, 0f), 0f)

            // Second frame processing tick immediately after (within 30s debounce window)
            alertCoordinator.onFrameProcessed(listOf(change), floatArrayOf(0f, 0f, 0f), 0f)

            // AlertManager.handleChanges should only be called once because the second call is debounced
            verify(mockAlertManager, times(1)).handleChanges(any(), any(), anyFloat())
        }
    }

    @Test
    fun should_allowAlertForDifferentObjects_when_processedInSameFrame() {
        val landmark1 = Landmark("lm_1", "p1", "chair", 0f, 0f, 0f, PermanenceClass.DYNAMIC, 3, 1000L, 1000L, 0.8f, 0.9f)
        val landmark2 = Landmark("lm_2", "p1", "table", 2f, 0f, 2f, PermanenceClass.SEMI_STATIC, 5, 1000L, 1000L, 0.9f, 0.95f)

        val change1 = DetectedChange(ChangeEventType.OBJECT_MOVED, landmark1, null, 0.70f, 0.2f, "Chair moved.")
        val change2 = DetectedChange(ChangeEventType.OBJECT_MOVED, landmark2, null, 0.80f, 0.1f, "Table moved.")

        runBlocking {
            `when`(mockAlertManager.handleChanges(any(), any(), anyFloat())).thenReturn(listOf(change1, change2))

            alertCoordinator.onFrameProcessed(listOf(change1, change2), floatArrayOf(0f, 0f, 0f), 0f)

            // Both non-debounced changes should be forwarded
            verify(mockAlertManager, times(1)).handleChanges(listOf(change1, change2), floatArrayOf(0f, 0f, 0f), 0f)
        }
    }
}
