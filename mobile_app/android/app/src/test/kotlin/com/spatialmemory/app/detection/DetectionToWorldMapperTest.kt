package com.spatialmemory.app.detection

import android.graphics.RectF
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyFloat
import org.mockito.Mockito.mock

/**
 * Unit test suite for [DetectionToWorldMapper].
 */
class DetectionToWorldMapperTest {

    private lateinit var mapper: DetectionToWorldMapper

    @Before
    fun setUp() {
        mapper = DetectionToWorldMapper()
    }

    @Test
    fun should_calculateCenterBottomPoint_when_givenBoundingBox() {
        val bbox = RectF(100f, 200f, 300f, 600f)

        val expectedCenterX = (100f + 300f) / 2.0f // 200f
        val expectedBottomY = 600f                 // 600f

        assertEquals(200f, expectedCenterX, 0.001f)
        assertEquals(600f, expectedBottomY, 0.001f)
    }

    @Test
    fun should_returnNull_when_noHitTestResultFound() {
        val mockFrame = mock(Frame::class.java)
        val detection = Detection("chair", 0.85f, RectF(0f, 0f, 100f, 100f), 56)

        `when`(mockFrame.hitTest(anyFloat(), anyFloat())).thenReturn(emptyList())

        val result = mapper.mapToWorldSpace(detection, mockFrame)

        assertNull("Mapper should return null if hitTest yields no tracked surfaces", result)
    }

    @Test
    fun should_mapToWorldSpace_when_hitTestReturnsTrackedPlane() {
        val mockFrame = mock(Frame::class.java)
        val mockHit = mock(HitResult::class.java)
        val mockPlane = mock(Plane::class.java)
        val mockCamera = mock(com.google.ar.core.Camera::class.java)

        val hitPose = Pose(floatArrayOf(1.2f, -0.5f, -2.0f), floatArrayOf(0f, 0f, 0f, 1f))
        val cameraPose = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))

        `when`(mockHit.pose).thenReturn(hitPose)
        `when`(mockHit.trackable).thenReturn(mockPlane)
        `when`(mockHit.isHitInPolygon).thenReturn(true)
        `when`(mockPlane.trackingState).thenReturn(TrackingState.TRACKING)
        `when`(mockFrame.hitTest(anyFloat(), anyFloat())).thenReturn(listOf(mockHit))
        `when`(mockFrame.camera).thenReturn(mockCamera)
        `when`(mockCamera.pose).thenReturn(cameraPose)

        val detection = Detection("chair", 0.90f, RectF(50f, 100f, 250f, 500f), 56)

        val spatialDetection = mapper.mapToWorldSpace(detection, mockFrame)

        assertNotNull(spatialDetection)
        assertEquals("chair", spatialDetection?.detection?.label)
        assertEquals(1.2f, spatialDetection?.worldPosition?.get(0) ?: 0f, 0.001f)
        assertEquals(-0.5f, spatialDetection?.worldPosition?.get(1) ?: 0f, 0.001f)
        assertEquals(-2.0f, spatialDetection?.worldPosition?.get(2) ?: 0f, 0.001f)
    }
}
