package com.spatialmemory.app.ar

import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Unit test suite for [MappingModeController].
 */
class MappingModeControllerTest {

    private lateinit var controller: MappingModeController

    @Before
    fun setUp() {
        @Suppress("UNCHECKED_CAST")
        controller = MappingModeController(
            context = null as Nothing?,
            database = null as Nothing?
        )
    }

    @Test
    fun should_reportNotReady_when_planeCountLessThanMinPlanes() {
        val mockSession = mock(Session::class.java)
        val mockFrame = mock(Frame::class.java)

        // 2 tracked planes (less than MIN_PLANES = 3), total area = 6.0 sq meters (>= MIN_AREA_SQ_METERS = 5.0)
        val plane1 = mockPlane(extentX = 2.0f, extentZ = 1.5f) // 3.0m²
        val plane2 = mockPlane(extentX = 2.0f, extentZ = 1.5f) // 3.0m²

        `when`(mockSession.getAllTrackables(Plane::class.java)).thenReturn(listOf(plane1, plane2))

        controller.startMapping("p1", "Room")
        val progress = controller.processFrame(mockSession, mockFrame)

        assertEquals(2, progress.planesDetected)
        assertEquals(6.0f, progress.areaCoveredSqM, 0.001f)
        assertFalse("isReadyToSave must be false when plane count < MIN_PLANES", progress.isReadyToSave)
    }

    @Test
    fun should_reportNotReady_when_areaCoveredLessThanMinArea() {
        val mockSession = mock(Session::class.java)
        val mockFrame = mock(Frame::class.java)

        // 3 tracked planes (>= MIN_PLANES = 3), total area = 3.0 sq meters (less than MIN_AREA_SQ_METERS = 5.0)
        val plane1 = mockPlane(extentX = 1.0f, extentZ = 1.0f) // 1.0m²
        val plane2 = mockPlane(extentX = 1.0f, extentZ = 1.0f) // 1.0m²
        val plane3 = mockPlane(extentX = 1.0f, extentZ = 1.0f) // 1.0m²

        `when`(mockSession.getAllTrackables(Plane::class.java)).thenReturn(listOf(plane1, plane2, plane3))

        controller.startMapping("p1", "Room")
        val progress = controller.processFrame(mockSession, mockFrame)

        assertEquals(3, progress.planesDetected)
        assertEquals(3.0f, progress.areaCoveredSqM, 0.001f)
        assertFalse("isReadyToSave must be false when total area < MIN_AREA_SQ_METERS", progress.isReadyToSave)
    }

    @Test
    fun should_reportReadyToSave_when_bothPlaneCountAndAreaThresholdsMet() {
        val mockSession = mock(Session::class.java)
        val mockFrame = mock(Frame::class.java)

        // 3 tracked planes (>= 3), total area = 6.0 sq meters (>= 5.0)
        val plane1 = mockPlane(extentX = 2.0f, extentZ = 1.0f) // 2.0m²
        val plane2 = mockPlane(extentX = 2.0f, extentZ = 1.0f) // 2.0m²
        val plane3 = mockPlane(extentX = 2.0f, extentZ = 1.0f) // 2.0m²

        `when`(mockSession.getAllTrackables(Plane::class.java)).thenReturn(listOf(plane1, plane2, plane3))

        controller.startMapping("p1", "Room")
        val progress = controller.processFrame(mockSession, mockFrame)

        assertEquals(3, progress.planesDetected)
        assertEquals(6.0f, progress.areaCoveredSqM, 0.001f)
        assertTrue("isReadyToSave must be true when both plane count and area thresholds are satisfied", progress.isReadyToSave)
    }

    private fun mockPlane(extentX: Float, extentZ: Float): Plane {
        val plane = mock(Plane::class.java)
        `when`(plane.trackingState).thenReturn(TrackingState.TRACKING)
        `when`(plane.extentX).thenReturn(extentX)
        `when`(plane.extentZ).thenReturn(extentZ)
        return plane
    }
}
