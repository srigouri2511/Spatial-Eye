package com.spatialmemory.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room database test for [LandmarkDao].
 *
 * Verifies 3D spatial radius querying ([LandmarkDao.getLandmarksNearPoint]), confirming it includes
 * entities within the radius and excludes boundary-adjacent ones outside it.
 */
@RunWith(AndroidJUnit4::class)
class SpatialMemoryDaoTest {

    private lateinit var database: SpatialMemoryDatabase
    private lateinit var landmarkDao: LandmarkDao
    private lateinit var placeDao: PlaceDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpatialMemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        landmarkDao = database.landmarkDao()
        placeDao = database.placeDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun should_returnOnlyLandmarksWithinRadius_when_queryingNearPoint() = runBlocking {
        val place = Place("p1", "Test Room", System.currentTimeMillis(), "path", System.currentTimeMillis())
        placeDao.insertPlace(place)

        // Focal point at (0, 0, 0)
        // Landmark 1: Inside radius (distance = 1.0m, radius = 2.0m)
        val insideLandmark = Landmark(
            id = "lm_inside",
            placeId = "p1",
            label = "chair",
            positionX = 1.0f, positionY = 0.0f, positionZ = 0.0f,
            permanenceClass = PermanenceClass.DYNAMIC,
            visitsConfirmed = 1,
            lastSeenAt = 1000L, firstSeenAt = 1000L,
            boundingBoxHeight = 0.8f, detectionConfidence = 0.9f
        )

        // Landmark 2: Outside radius (distance = 3.0m, radius = 2.0m)
        val outsideLandmark = Landmark(
            id = "lm_outside",
            placeId = "p1",
            label = "table",
            positionX = 3.0f, positionY = 0.0f, positionZ = 0.0f,
            permanenceClass = PermanenceClass.SEMI_STATIC,
            visitsConfirmed = 1,
            lastSeenAt = 1000L, firstSeenAt = 1000L,
            boundingBoxHeight = 0.9f, detectionConfidence = 0.95f
        )

        landmarkDao.insertLandmarks(listOf(insideLandmark, outsideLandmark))

        // Query radius = 2.0m around (0, 0, 0)
        val results = landmarkDao.getLandmarksNearPoint("p1", 0.0f, 0.0f, 0.0f, 2.0f)

        assertEquals(1, results.size)
        assertEquals("lm_inside", results[0].id)
    }
}
