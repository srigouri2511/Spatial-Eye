package com.spatialmemory.app.voice

import com.spatialmemory.app.alerting.AlertManager
import com.spatialmemory.app.data.ChangeEventDao
import com.spatialmemory.app.data.Place
import com.spatialmemory.app.data.SpatialMemoryDatabase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyFloat
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * Unit test suite for [QueryResponder].
 */
class QueryResponderTest {

    private lateinit var mockDatabase: SpatialMemoryDatabase
    private lateinit var mockChangeEventDao: ChangeEventDao
    private lateinit var mockAlertManager: AlertManager
    private lateinit var queryResponder: QueryResponder

    @Before
    fun setUp() {
        mockDatabase = mock(SpatialMemoryDatabase::class.java)
        mockChangeEventDao = mock(ChangeEventDao::class.java)
        mockAlertManager = mock(AlertManager::class.java)

        `when`(mockDatabase.changeEventDao()).thenReturn(mockChangeEventDao)
        queryResponder = QueryResponder(mockDatabase)
    }

    @Test
    fun should_queryChangeEventsSinceLastVisit_when_WhatsNewHereIntentReceived() {
        val lastUpdatedTimestamp = 1600000000000L
        val place = Place("p1", "Living Room", lastUpdatedTimestamp, "path", 1500000000000L)

        `when`(mockChangeEventDao.getRecentChangeEventsForPlace("p1", lastUpdatedTimestamp))
            .thenReturn(flowOf(emptyList()))

        runBlocking {
            queryResponder.respond(VoiceIntent.WhatsNewHere, place, mockAlertManager)

            // Verify DAO was queried using place.lastUpdated timestamp
            verify(mockChangeEventDao).getRecentChangeEventsForPlace("p1", lastUpdatedTimestamp)
        }
    }

    @Test
    fun should_speakUnlocalizedWarning_when_currentPlaceIsNullOnWhatsNewHere() {
        runBlocking {
            queryResponder.respond(VoiceIntent.WhatsNewHere, null, mockAlertManager)

            verify(mockAlertManager).handleChanges(any(), any(), anyFloat())
        }
    }

    @Test
    fun should_speakLocationName_when_WhereAmIIntentReceived() {
        val place = Place("p1", "Office Lobby", 1000L, "path", 1000L)

        runBlocking {
            queryResponder.respond(VoiceIntent.WhereAmI, place, mockAlertManager)

            verify(mockAlertManager).handleChanges(any(), any(), anyFloat())
        }
    }
}
