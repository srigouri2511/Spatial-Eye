package com.spatialmemory.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [VoiceQueryManager] intent classification logic.
 */
class VoiceQueryManagerTest {

    private lateinit var queryManager: VoiceQueryManager

    @Before
    fun setUp() {
        @Suppress("UNCHECKED_CAST")
        queryManager = VoiceQueryManager(null as Nothing?)
    }

    @Test
    fun should_classifyAsWhatsNewHere_when_utteranceContainsWhatsNew() {
        val intent = queryManager.classifyIntent("what's new in this room?")
        assertEquals(VoiceIntent.WhatsNewHere, intent)
    }

    @Test
    fun should_classifyAsWhatsNewSince_when_utteranceContainsTimeframe() {
        val intent = queryManager.classifyIntent("what changed since yesterday?")
        assertTrue("Should classify as WhatsNewSince", intent is VoiceIntent.WhatsNewSince)
        assertEquals("since yesterday", (intent as VoiceIntent.WhatsNewSince).timeframe)
    }

    @Test
    fun should_classifyAsWhereAmI_when_utteranceAsksLocation() {
        val intent = queryManager.classifyIntent("where am i")
        assertEquals(VoiceIntent.WhereAmI, intent)
    }

    @Test
    fun should_classifyAsStartMapping_when_utteranceContainsStartMapping() {
        val intent = queryManager.classifyIntent("start mapping room kitchen")
        assertTrue("Should classify as StartMapping", intent is VoiceIntent.StartMapping)
    }

    @Test
    fun should_classifyAsReadLastAlert_when_utteranceAsksRepeat() {
        val intent = queryManager.classifyIntent("repeat last alert")
        assertEquals(VoiceIntent.ReadLastAlert, intent)
    }

    @Test
    fun should_classifyAsUnknown_when_utteranceIsUnrelatedNearMiss() {
        val intent1 = queryManager.classifyIntent("what is the weather today")
        assertTrue("Near miss should classify as Unknown", intent1 is VoiceIntent.Unknown)

        val intent2 = queryManager.classifyIntent("open music player")
        assertTrue("Unrelated command should classify as Unknown", intent2 is VoiceIntent.Unknown)
    }
}
