package com.spatialmemory.app.diffing

import com.spatialmemory.app.data.ChangeEventType
import com.spatialmemory.app.data.Landmark
import com.spatialmemory.app.data.PermanenceClass
import com.spatialmemory.app.data.Place
import com.spatialmemory.app.data.WalkCorridor
import com.spatialmemory.app.detection.Detection
import com.spatialmemory.app.detection.SpatialDetection
import android.graphics.RectF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [ChangeDetectionEngine].
 *
 * Tests 3D matching boundaries, event classification, corridor distance geometry,
 * and multi-factor safety severity scoring.
 */
class ChangeDetectionEngineTest {

    private lateinit var engine: ChangeDetectionEngine

    @Before
    fun setUp() {
        @Suppress("UNCHECKED_CAST")
        engine = ChangeDetectionEngine(null as Nothing?)
    }

    // ==========================================
    // 1. 3D Euclidean Matching Boundary Tests
    // ==========================================

    @Test
    fun should_classifyAsMoved_when_detectionWithinMatchingRadiusButBeyondMovementThreshold() {
        val place = Place("p1", "Room", System.currentTimeMillis(), "path", System.currentTimeMillis())
        val landmark = Landmark(
            id = "lm_chair",
            placeId = "p1",
            label = "chair",
            positionX = 0.0f, positionY = 0.0f, positionZ = 0.0f,
            permanenceClass = PermanenceClass.DYNAMIC,
            visitsConfirmed = 5,
            lastSeenAt = 1000L, firstSeenAt = 1000L,
            boundingBoxHeight = 0.8f, detectionConfidence = 0.9f
        )

        // Live detection shifted by 0.4m along X axis (inside 0.5m matching radius, beyond 0.3m movement threshold)
        val liveDetection = SpatialDetection(
            detection = Detection("chair", 0.9f, RectF(0f, 0f, 100f, 100f), 56),
            worldPosition = floatArrayOf(0.4f, 0.0f, 0.0f),
            estimatedHeightMeters = 0.8f,
            distanceFromCameraMeters = 1.5f
        )

        val corridor = WalkCorridor("c1", "p1", listOf(Pair(0f, -5f), Pair(0f, 5f)), 2.0f)

        runBlocking {
            val changes = engine.diff(place, listOf(liveDetection), listOf(landmark), listOf(corridor))

            assertEquals(1, changes.size)
            val change = changes[0]
            assertEquals(ChangeEventType.OBJECT_MOVED, change.eventType)
            assertEquals("lm_chair", change.landmark?.id)
        }
    }

    @Test
    fun should_classifyAsNewObject_when_detectionOutsideMatchingRadius() {
        val place = Place("p1", "Room", System.currentTimeMillis(), "path", System.currentTimeMillis())
        val landmark = Landmark(
            id = "lm_chair",
            placeId = "p1",
            label = "chair",
            positionX = 0.0f, positionY = 0.0f, positionZ = 0.0f,
            permanenceClass = PermanenceClass.DYNAMIC,
            visitsConfirmed = 5,
            lastSeenAt = 1000L, firstSeenAt = 1000L,
            boundingBoxHeight = 0.8f, detectionConfidence = 0.9f
        )

        // Live detection shifted by 0.55m along X axis (outside 0.5m matching radius boundary)
        val liveDetection = SpatialDetection(
            detection = Detection("chair", 0.9f, RectF(0f, 0f, 100f, 100f), 56),
            worldPosition = floatArrayOf(0.55f, 0.0f, 0.0f),
            estimatedHeightMeters = 0.8f,
            distanceFromCameraMeters = 1.5f
        )

        val corridor = WalkCorridor("c1", "p1", listOf(Pair(0f, -5f), Pair(0f, 5f)), 2.0f)

        runBlocking {
            val changes = engine.diff(place, listOf(liveDetection), listOf(landmark), listOf(corridor))

            val newObjectChange = changes.firstOrNull { it.eventType == ChangeEventType.NEW_OBJECT }
            assertTrue("Object outside 0.5m matching radius should be classified as NEW_OBJECT", newObjectChange != null)
            assertNull(newObjectChange?.landmark)
        }
    }

    // ==========================================
    // 2. Corridor Distance Geometry Tests
    // ==========================================

    @Test
    fun should_returnZeroDistance_when_pointDirectlyOnCorridorCenterline() {
        val corridor = WalkCorridor("c1", "p1", listOf(Pair(0f, 0f), Pair(0f, 10f)), 2.0f)
        val distance = engine.distanceFromCorridor(floatArrayOf(0.0f, 0.0f, 5.0f), listOf(corridor))
        assertEquals(0.0f, distance, 0.001f)
    }

    @Test
    fun should_returnZeroDistance_when_pointAtCorridorEdge() {
        // Corridor width = 2.0m (clearance boundary = 1.0m half-width)
        val corridor = WalkCorridor("c1", "p1", listOf(Pair(0f, 0f), Pair(0f, 10f)), 2.0f)
        // Point at X = 1.0m is directly on the clearance edge
        val distance = engine.distanceFromCorridor(floatArrayOf(1.0f, 0.0f, 5.0f), listOf(corridor))
        assertEquals(0.0f, distance, 0.001f)
    }

    @Test
    fun should_returnPerpendicularDistance_when_pointWellOutsideCorridor() {
        val corridor = WalkCorridor("c1", "p1", listOf(Pair(0f, 0f), Pair(0f, 10f)), 2.0f)
        // Point at X = 3.5m -> 3.5m - 1.0m half-width = 2.5m from boundary
        val distance = engine.distanceFromCorridor(floatArrayOf(3.5f, 0.0f, 5.0f), listOf(corridor))
        assertEquals(2.5f, distance, 0.001f)
    }

    // ==========================================
    // 3. Severity Scoring Alert Tier Tests
    // ==========================================

    @Test
    fun should_landInHighAlertTier_when_tripHazardDirectlyInCorridor() {
        val score = engine.computeSeverity(
            eventType = ChangeEventType.NEW_OBJECT,
            permanenceClass = PermanenceClass.DYNAMIC,
            distanceFromCorridor = 0.0f,
            estimatedHeightMeters = 0.40f, // Ground trip hazard (0.1m - 0.8m)
            detectionConfidence = 0.95f,
            isHighTrafficCorridor = true
        )

        assertTrue("Trip hazard directly in corridor must produce high severity (>= 0.65)", score >= AlertThresholds.SEVERITY_HIGH_MIN)
    }

    @Test
    fun should_landInLowAlertTier_when_moderateHazardNearCorridorEdge() {
        val score = engine.computeSeverity(
            eventType = ChangeEventType.OBJECT_MOVED,
            permanenceClass = PermanenceClass.SEMI_STATIC,
            distanceFromCorridor = 1.0f,
            estimatedHeightMeters = 1.0f,
            detectionConfidence = 0.80f,
            isHighTrafficCorridor = false
        )

        assertTrue("Moderate hazard near edge should land in Low tier (0.35 <= score < 0.65)",
            score >= AlertThresholds.SEVERITY_SILENT_MAX && score < AlertThresholds.SEVERITY_LOW_MAX)
    }

    @Test
    fun should_landInSilentTier_when_distantNonHazardousObject() {
        val score = engine.computeSeverity(
            eventType = ChangeEventType.NEW_OBJECT,
            permanenceClass = PermanenceClass.DYNAMIC,
            distanceFromCorridor = 2.5f,
            estimatedHeightMeters = 1.0f,
            detectionConfidence = 0.70f,
            isHighTrafficCorridor = false
        )

        assertTrue("Distant non-hazardous object must land in Silent tier (< 0.35)", score < AlertThresholds.SEVERITY_SILENT_MAX)
    }
}
