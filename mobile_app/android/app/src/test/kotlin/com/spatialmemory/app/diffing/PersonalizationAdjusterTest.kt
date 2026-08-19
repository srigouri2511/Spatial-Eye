package com.spatialmemory.app.diffing

import com.spatialmemory.app.data.ChangeEvent
import com.spatialmemory.app.data.ChangeEventDao
import com.spatialmemory.app.data.ChangeEventType
import com.spatialmemory.app.data.SpatialMemoryDatabase
import com.spatialmemory.app.data.UserFeedback
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Unit test suite for [PersonalizationAdjuster].
 *
 * Verifies adaptive threshold adjustments derived from user feedback logs.
 */
class PersonalizationAdjusterTest {

    private lateinit var mockDatabase: SpatialMemoryDatabase
    private lateinit var mockChangeEventDao: ChangeEventDao
    private lateinit var adjuster: PersonalizationAdjuster

    @Before
    fun setUp() {
        mockDatabase = mock(SpatialMemoryDatabase::class.java)
        mockChangeEventDao = mock(ChangeEventDao::class.java)
        `when`(mockDatabase.changeEventDao()).thenReturn(mockChangeEventDao)

        adjuster = PersonalizationAdjuster(mockDatabase)
    }

    @Test
    fun should_maintainDefaultThresholds_when_singleNotUsefulFeedbackRecorded() {
        val singleNotUsefulEvent = ChangeEvent(
            id = "e1",
            placeId = "place_1",
            landmarkId = "lm_1",
            eventType = ChangeEventType.NEW_OBJECT,
            severityScore = 0.5f,
            detectedAt = System.currentTimeMillis(),
            userFeedback = UserFeedback.NOT_USEFUL
        )

        `when`(mockChangeEventDao.getRecentChangeEventsForPlace("place_1", 0L))
            .thenReturn(flowOf(listOf(singleNotUsefulEvent)))

        runBlocking {
            val thresholds = adjuster.adjustThresholds("place_1")

            assertEquals(AlertThresholds.SEVERITY_SILENT_MAX, thresholds.minSeverityToAlert, 0.001f)
            assertEquals(ChangeDetectionEngine.CORRIDOR_MAX_DIST_EFFECT_METERS, thresholds.maxCorridorDistanceMeters, 0.001f)
        }
    }

    @Test
    fun should_elevateSeverityFloor_when_threeNotUsefulFeedbacksRecorded() {
        val notUsefulEvents = (1..3).map { i ->
            ChangeEvent(
                id = "e$i",
                placeId = "place_1",
                landmarkId = "lm_$i",
                eventType = ChangeEventType.OBJECT_MOVED,
                severityScore = 0.45f,
                detectedAt = System.currentTimeMillis(),
                userFeedback = UserFeedback.NOT_USEFUL
            )
        }

        `when`(mockChangeEventDao.getRecentChangeEventsForPlace("place_1", 0L))
            .thenReturn(flowOf(notUsefulEvents))

        runBlocking {
            val thresholds = adjuster.adjustThresholds("place_1")

            // 3 NOT_USEFUL events should elevate min severity to alert to 0.50f
            assertEquals(0.50f, thresholds.minSeverityToAlert, 0.001f)
            assertEquals(ChangeDetectionEngine.CORRIDOR_MAX_DIST_EFFECT_METERS, thresholds.maxCorridorDistanceMeters, 0.001f)
        }
    }

    @Test
    fun should_tightenDistanceCutoffAndElevateSeverity_when_fiveNotUsefulFeedbacksRecorded() {
        val notUsefulEvents = (1..5).map { i ->
            ChangeEvent(
                id = "e$i",
                placeId = "place_1",
                landmarkId = "lm_$i",
                eventType = ChangeEventType.NEW_OBJECT,
                severityScore = 0.40f,
                detectedAt = System.currentTimeMillis(),
                userFeedback = UserFeedback.NOT_USEFUL
            )
        }

        `when`(mockChangeEventDao.getRecentChangeEventsForPlace("place_1", 0L))
            .thenReturn(flowOf(notUsefulEvents))

        runBlocking {
            val thresholds = adjuster.adjustThresholds("place_1")

            // 5 NOT_USEFUL events should elevate min severity to 0.50f and tighten corridor distance to 1.0m
            assertEquals(0.50f, thresholds.minSeverityToAlert, 0.001f)
            assertEquals(1.0f, thresholds.maxCorridorDistanceMeters, 0.001f)
        }
    }
}
