package com.spatialmemory.app.diffing

/**
 * Defines alert severity boundaries for categorizing detected spatial changes.
 *
 * Used by the notification and audio feedback engines to route alerts appropriately:
 * - **Silent** (`< SEVERITY_SILENT_MAX`): Changes are logged to database for baseline memory updates, but generate no active user alert.
 * - **Low / Haptic** (`SEVERITY_SILENT_MAX <= score < SEVERITY_LOW_MAX`): Triggers a subtle haptic vibration pulse and brief audio cue.
 * - **High / Spoken** (`>= SEVERITY_LOW_MAX`): Triggers a full spoken auditory description via Text-To-Speech (TTS).
 */
object AlertThresholds {
    /** Upper severity limit for silent logging. Below 0.35, changes are logged without active user disturbance. */
    const val SEVERITY_SILENT_MAX: Float = 0.35f

    /** Upper severity limit for haptic alerts. Between 0.35 and 0.65, changes trigger subtle haptic/tone warnings. */
    const val SEVERITY_LOW_MAX: Float = 0.65f

    /** Minimum severity required to trigger a full spoken auditory description alert. */
    const val SEVERITY_HIGH_MIN: Float = 0.65f
}
