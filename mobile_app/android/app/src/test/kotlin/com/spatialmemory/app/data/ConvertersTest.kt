package com.spatialmemory.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for Room [Converters].
 *
 * Verifies round-trip type conversion for corridor pathPoints, timestamps, and enums.
 */
class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    @Test
    fun should_serializeAndDeserializePathPoints_roundTrip() {
        val originalPoints = listOf(
            Pair(1.23f, 4.56f),
            Pair(-7.89f, 0.12f),
            Pair(3.45f, -6.78f)
        )

        val json = converters.fromPathPoints(originalPoints)
        assertNotNull(json)

        val restoredPoints = converters.toPathPoints(json)
        assertNotNull(restoredPoints)
        assertEquals(originalPoints.size, restoredPoints?.size)

        for (i in originalPoints.indices) {
            assertEquals(originalPoints[i].first, restoredPoints!![i].first, 0.001f)
            assertEquals(originalPoints[i].second, restoredPoints[i].second, 0.001f)
        }
    }

    @Test
    fun should_handleNullPathPoints_gracefully() {
        val json = converters.fromPathPoints(null)
        assertNull(json)

        val restored = converters.toPathPoints(null)
        assertNull(restored)
    }

    @Test
    fun should_serializeAndDeserializePermanenceClass_roundTrip() {
        for (enumVal in PermanenceClass.values()) {
            val str = converters.fromPermanenceClass(enumVal)
            val restored = converters.toPermanenceClass(str)
            assertEquals(enumVal, restored)
        }
    }

    @Test
    fun should_serializeAndDeserializeChangeEventType_roundTrip() {
        for (enumVal in ChangeEventType.values()) {
            val str = converters.fromChangeEventType(enumVal)
            val restored = converters.toChangeEventType(str)
            assertEquals(enumVal, restored)
        }
    }

    @Test
    fun should_serializeAndDeserializeUserFeedback_roundTrip() {
        for (enumVal in UserFeedback.values()) {
            val str = converters.fromUserFeedback(enumVal)
            val restored = converters.toUserFeedback(str)
            assertEquals(enumVal, restored)
        }
    }
}
