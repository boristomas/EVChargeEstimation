package com.chupacabra.evchargeestimation

import com.chupacabra.evchargeestimation.domain.ChargeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeEstimatorTest {

    @Test
    fun halfRemaining_scalesHalfTime() {
        // At 50%, 60 min to full → to 75% needs half of remaining (25 of 50) → 30 min
        val result = ChargeEstimator.estimate(
            ChargeEstimator.Input(currentPercent = 50, minutesToFull = 60, desiredPercent = 75)
        )
        assertEquals(30, result.minutesToDesired)
        assertFalse(result.isAlreadyThere)
    }

    @Test
    fun toFull_returnsFullMinutes() {
        val result = ChargeEstimator.estimate(
            ChargeEstimator.Input(currentPercent = 40, minutesToFull = 90, desiredPercent = 100)
        )
        assertEquals(90, result.minutesToDesired)
    }

    @Test
    fun alreadyAtDesired_isZero() {
        val result = ChargeEstimator.estimate(
            ChargeEstimator.Input(currentPercent = 80, minutesToFull = 40, desiredPercent = 80)
        )
        assertEquals(0, result.minutesToDesired)
        assertTrue(result.isAlreadyThere)
    }

    @Test
    fun desiredBelowCurrent_isZero() {
        val result = ChargeEstimator.estimate(
            ChargeEstimator.Input(currentPercent = 90, minutesToFull = 20, desiredPercent = 70)
        )
        assertEquals(0, result.minutesToDesired)
        assertTrue(result.isAlreadyThere)
    }

    @Test
    fun formatDuration_mixed() {
        assertEquals("1h 5m", ChargeEstimator.formatDuration(65))
        assertEquals("45 min", ChargeEstimator.formatDuration(45))
        assertEquals("2h", ChargeEstimator.formatDuration(120))
        assertEquals("0 min", ChargeEstimator.formatDuration(0))
    }

    @Test
    fun splitAndTotalMinutes_roundTrip() {
        assertEquals(2 to 0, ChargeEstimator.splitMinutes(120))
        assertEquals(3 to 25, ChargeEstimator.splitMinutes(205))
        assertEquals(120, ChargeEstimator.totalMinutes(2, 0))
        assertEquals(205, ChargeEstimator.totalMinutes(3, 25))
    }

    @Test
    fun invalidCurrent_returnsError() {
        val result = ChargeEstimator.estimate(
            ChargeEstimator.Input(currentPercent = 140, minutesToFull = 30, desiredPercent = 80)
        )
        assertTrue(result.message != null)
    }
}
