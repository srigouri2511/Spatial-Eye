package com.spatialmemory.app.alerting

import android.content.Context
import com.spatialmemory.app.data.SpatialMemoryDatabase
import com.spatialmemory.app.diffing.AlertThresholds
import com.spatialmemory.app.diffing.DetectedChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Coordinates frame-by-frame alert routing, rate limiting (debouncing), and voice feedback capture.
 *
 * Receives scored [DetectedChange] objects from the camera detection loop, applies in-memory rate limiting
 * to prevent repetitive nagging alerts, delegates to [AlertManager] for multimodal delivery, and triggers
 * [FeedbackListener] for high-severity spoken alerts.
 *
 * @param context Application context.
 * @param database [SpatialMemoryDatabase] instance.
 * @param alertManager [AlertManager] instance for multimodal alert delivery.
 * @param feedbackListener [FeedbackListener] instance for capturing voice feedback.
 */
class AlertCoordinator(
    private val context: Context,
    private val database: SpatialMemoryDatabase,
    private val alertManager: AlertManager = AlertManager(context, database),
    private val feedbackListener: FeedbackListener = FeedbackListener(context, database)
) : AutoCloseable {

    companion object {
        /** In-memory rate limiting window (in milliseconds) before re-alerting about the same spatial object. */
        const val DEBOUNCE_INTERVAL_MS: Long = 30_000L // 30 seconds
    }

    /**
     * In-memory map tracking `alertKey -> lastAlertedAtTimestamp`.
     *
     * ### Debouncing Rationale:
     * Intentionally maintained in volatile memory (resets each app session) to prevent repeating "chair ahead"
     * on every 30 FPS camera frame tick while a visually impaired user is standing or walking slowly near an object.
     */
    private val recentAlertsMap = mutableMapOf<String, Long>()
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Invoked on processed camera frames containing detected spatial changes.
     *
     * @param changes Scored changes from diffing engine.
     * @param userPosition User 3D camera position `[x, y, z]`.
     * @param userHeading User camera facing heading in radians.
     */
    suspend fun onFrameProcessed(
        changes: List<DetectedChange>,
        userPosition: FloatArray,
        userHeading: Float
    ) {
        val now = System.currentTimeMillis()

        // Step 1: Filter out debounced changes alerted within DEBOUNCE_INTERVAL_MS
        val freshChanges = changes.filter { change ->
            val key = getAlertKey(change)
            val lastAlerted = recentAlertsMap[key] ?: 0L
            (now - lastAlerted) >= DEBOUNCE_INTERVAL_MS
        }

        if (freshChanges.isEmpty()) return

        // Step 2: Deliver multimodal alerts
        val deliveredAlerts = alertManager.handleChanges(freshChanges, userPosition, userHeading)

        // Step 3: Update debounce map for delivered alerts
        for (change in deliveredAlerts) {
            val key = getAlertKey(change)
            recentAlertsMap[key] = now
        }

        // Step 4: For high-severity spoken alerts, trigger voice feedback listener in background
        for (change in deliveredAlerts) {
            if (change.severityScore >= AlertThresholds.SEVERITY_HIGH_MIN) {
                val dummyEventId = change.landmark?.id ?: "event_${now}"
                scope.launch {
                    feedbackListener.listenForFeedback(dummyEventId, timeoutMs = 4000L)
                }
            }
        }
    }

    private fun getAlertKey(change: DetectedChange): String {
        return change.landmark?.id
            ?: "${change.liveDetection?.detection?.label}_${(change.distanceFromCorridorMeters * 10).toInt()}"
    }

    fun release() {
        close()
    }

    override fun close() {
        alertManager.close()
        feedbackListener.close()
        recentAlertsMap.clear()
    }
}
