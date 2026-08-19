package com.spatialmemory.app.alerting

import com.spatialmemory.app.diffing.AlertThresholds
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [AlertManager] relative direction geometry and severity tier boundaries.
 */
class AlertManagerTest {

    private lateinit var alertManager: AlertManager

    @Before
    fun setUp() {
        @Suppress("UNCHECKED_CAST")
        alertManager = AlertManager(null as Nothing?, null as Nothing?)
    }

    // ==========================================
    // 1. Relative Direction Calculation Tests
    // ==========================================

    @Test
    fun should_returnAhead_when_targetDirectlyInFrontOfUser() {
        val userPos = floatArrayOf(0.0f, 0.0f, 0.0f)
        val targetPos = floatArrayOf(0.0f, 0.0f, -3.0f) // 3m straight ahead along Z axis
        val userHeading = 0.0f // Facing north / straight ahead

        val direction = alertManager.relativeDirection(targetPos, userPos, userHeading)
        assertEquals(Direction.AHEAD, direction)
    }

    @Test
    fun should_returnSlightlyLeft_when_targetAtMinusThirtyDegrees() {
        val userPos = floatArrayOf(0.0f, 0.0f, 0.0f)
        val targetPos = floatArrayOf(-1.0f, 0.0f, -2.0f) // ~ -26.5 degrees left
        val userHeading = 0.0f

        val direction = alertManager.relativeDirection(targetPos, userPos, userHeading)
        assertEquals(Direction.SLIGHTLY_LEFT, direction)
    }

    @Test
    fun should_returnLeft_when_targetAtMinusNinetyDegrees() {
        val userPos = floatArrayOf(0.0f, 0.0f, 0.0f)
        val targetPos = floatArrayOf(-3.0f, 0.0f, 0.0f) // 90 degrees to the left
        val userHeading = 0.0f

        val direction = alertManager.relativeDirection(targetPos, userPos, userHeading)
        assertEquals(Direction.LEFT, direction)
    }

    @Test
    fun should_returnRight_when_targetAtPlusNinetyDegrees() {
        val userPos = floatArrayOf(0.0f, 0.0f, 0.0f)
        val targetPos = floatArrayOf(3.0f, 0.0f, 0.0f) // 90 degrees to the right
        val userHeading = 0.0f

        val direction = alertManager.relativeDirection(targetPos, userPos, userHeading)
        assertEquals(Direction.RIGHT, direction)
    }

    @Test
    fun should_returnBehind_when_targetDirectlyBehindUser() {
        val userPos = floatArrayOf(0.0f, 0.0f, 0.0f)
        val targetPos = floatArrayOf(0.0f, 0.0f, 3.0f) // 3m behind user
        val userHeading = 0.0f

        val direction = alertManager.relativeDirection(targetPos, userPos, userHeading)
        assertEquals(Direction.BEHIND, direction)
    }

    // ==========================================
    // 2. Alert Severity Tier Boundary Values
    // ==========================================

    @Test
    fun should_verifyThresholdConstants() {
        assertEquals(0.35f, AlertThresholds.SEVERITY_SILENT_MAX, 0.001f)
        assertEquals(0.65f, AlertThresholds.SEVERITY_LOW_MAX, 0.001f)
        assertEquals(0.65f, AlertThresholds.SEVERITY_HIGH_MIN, 0.001f)
    }
}
