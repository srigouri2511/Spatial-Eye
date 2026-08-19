package com.spatialmemory.app.voice

/**
 * Defines recognized voice command trigger phrases and keywords per [VoiceIntent].
 *
 * Used for documentation, testing, and tuning utterance matching without modifying the core classifier structure.
 */
object VoiceCommandGrammar {

    val WHATS_NEW_PHRASES = listOf(
        "what's new",
        "anything changed",
        "anything different",
        "what changed",
        "any updates",
        "room status",
        "status report"
    )

    val WHATS_NEW_SINCE_PHRASES = listOf(
        "since yesterday",
        "since today",
        "since this week",
        "since last week",
        "since morning"
    )

    val START_MAPPING_PHRASES = listOf(
        "start mapping",
        "map this room",
        "scan this place",
        "create map",
        "new place",
        "start scan"
    )

    val WHERE_AM_I_PHRASES = listOf(
        "where am i",
        "what place is this",
        "current place",
        "identify location",
        "which room"
    )

    val READ_LAST_ALERT_PHRASES = listOf(
        "repeat last alert",
        "what was that",
        "read last alert",
        "say again",
        "repeat",
        "what did you say"
    )
}
