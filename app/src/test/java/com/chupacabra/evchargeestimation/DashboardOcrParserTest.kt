package com.chupacabra.evchargeestimation

import com.chupacabra.evchargeestimation.domain.DashboardOcrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parser fixtures include synthetic strings and realistic OCR dumps modelled on
 * the Peugeot-style cluster photos in app/sampledata/ (52 %, 3 h 25 min to full,
 * charge rate "42 km per hour", range 174 km, odo 15469 km).
 */
class DashboardOcrParserTest {

    @Test
    fun parsesPercentAndMinutes() {
        val parsed = DashboardOcrParser.parse("Battery 42%  Remaining 55 min to full")
        assertEquals(42, parsed.currentPercent)
        assertEquals(55, parsed.minutesToFull)
    }

    @Test
    fun parsesHoursAndMinutes() {
        val parsed = DashboardOcrParser.parse("SOC 67% Charge time 1h 20m")
        assertEquals(67, parsed.currentPercent)
        assertEquals(80, parsed.minutesToFull)
    }

    @Test
    fun parsesClockStyleDuration() {
        val parsed = DashboardOcrParser.parse("Charge 35%  0:45 until full")
        assertEquals(35, parsed.currentPercent)
        assertEquals(45, parsed.minutesToFull)
    }

    @Test
    fun emptyText_returnsNulls() {
        val parsed = DashboardOcrParser.parse("")
        assertNull(parsed.currentPercent)
        assertNull(parsed.minutesToFull)
    }

    @Test
    fun percentOnly() {
        val parsed = DashboardOcrParser.parse("Currently at 81%")
        assertEquals(81, parsed.currentPercent)
        assertNotNull(parsed.rawText)
    }

    // --- Sample dashboard photos (app/sampledata/*.jpg) ---

    @Test
    fun sampleDashboard_cleanLayout() {
        // Ideal multi-line OCR of the Peugeot charging screen
        val ocr = """
            CHARGING COMPLETE IN 3 h 25 min
            Charge of 42 km per hour
            52 %
            15469 km
            174 km
        """.trimIndent()

        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(52, parsed.currentPercent)
        assertEquals(3 * 60 + 25, parsed.minutesToFull)
    }

    @Test
    fun sampleDashboard_singleLineOcr() {
        val ocr =
            "CHARGING COMPLETE IN 3 h 25 min Charge of 42 km per hour 52 % 15469 km 174 km"
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(52, parsed.currentPercent)
        assertEquals(205, parsed.minutesToFull)
    }

    @Test
    fun sampleDashboard_tightSpacing() {
        // OCR often drops spaces around units
        val ocr = "CHARGING COMPLETE IN 3h 25min  Charge of 42 km per hour  52%"
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(52, parsed.currentPercent)
        assertEquals(205, parsed.minutesToFull)
    }

    @Test
    fun sampleDashboard_doesNotUseChargeRateAsPercent() {
        // Without scrubbing, a naive "charge … 42" rule would pick the rate
        val ocr = "CHARGING COMPLETE IN 3 h 25 min Charge of 42 km per hour 52 %"
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(52, parsed.currentPercent)
        assertEquals(205, parsed.minutesToFull)
    }

    @Test
    fun sampleDashboard_ignoresRangeAndOdometer() {
        val ocr = "52% 174 km 15469 km COMPLETE IN 1 h 10 min"
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(52, parsed.currentPercent)
        assertEquals(70, parsed.minutesToFull)
    }

    @Test
    fun sampleDashboard_completeInWithoutChargingWord() {
        val ocr = "COMPLETE IN 3 h 25 min  52%"
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(52, parsed.currentPercent)
        assertEquals(205, parsed.minutesToFull)
    }

    @Test
    fun sampleDashboard_ocrSplitLines() {
        // ML Kit often emits blocks in arbitrary order
        val ocr = """
            174 km
            52 %
            Charge of 42 km per hour
            CHARGING COMPLETE IN 3 h 25 min
            15469 km
        """.trimIndent()
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(52, parsed.currentPercent)
        assertEquals(205, parsed.minutesToFull)
    }

    @Test
    fun prefersPercentSignOverChargeLabel() {
        // "Charge 40" style label must lose to explicit "52%"
        val ocr = "Charge 40 remaining  52%  30 min"
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(52, parsed.currentPercent)
        assertEquals(30, parsed.minutesToFull)
    }

    @Test
    fun chargingCompleteIn_minutesOnly() {
        val parsed = DashboardOcrParser.parse("CHARGING COMPLETE IN 45 min  60%")
        assertEquals(60, parsed.currentPercent)
        assertEquals(45, parsed.minutesToFull)
    }

    @Test
    fun chargingCompleteIn_hoursOnly() {
        val parsed = DashboardOcrParser.parse("CHARGING COMPLETE IN 2 h  30%")
        assertEquals(30, parsed.currentPercent)
        assertEquals(120, parsed.minutesToFull)
    }

    // --- Chevrolet Bolt–style cluster (app/sampledata/d9735234-c6cd-4cf0-b17c-247b7a1e72b2.jpg) ---

    @Test
    fun boltDashboard_estimatedChargeCompleteInHrs() {
        // Realistic OCR dump of the Bolt charging screen from issue #1
        val ocr = """
            2:31 pm
            12/21/2018
            charging in progress
            240 V
            estimated charge
            complete in 1 hrs 02 min
            32931 mi
            68 miles range
            66°F
            80%
        """.trimIndent()

        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(80, parsed.currentPercent)
        assertEquals(62, parsed.minutesToFull)
    }

    @Test
    fun boltDashboard_singleLineOcr() {
        val ocr =
            "charging in progress 240 V estimated charge complete in 1 hrs 02 min 32931 mi 68 miles range 80% 66°F"
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(80, parsed.currentPercent)
        assertEquals(62, parsed.minutesToFull)
    }

    @Test
    fun boltDashboard_ignoresWallClockAmPm() {
        // "2:31 pm" must not become 151 minutes-to-full
        val ocr = "2:31 pm estimated charge complete in 1 hrs 02 min 80%"
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(80, parsed.currentPercent)
        assertEquals(62, parsed.minutesToFull)
    }

    @Test
    fun boltDashboard_ignoresMilesRangeAndVoltage() {
        val ocr = "240 V  68 miles range  32931 mi  80%  complete in 45 min"
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(80, parsed.currentPercent)
        assertEquals(45, parsed.minutesToFull)
    }

    @Test
    fun boltDashboard_hrsWithoutEstimatedPrefix() {
        val ocr = "complete in 1 hrs 02 min  55%"
        val parsed = DashboardOcrParser.parse(ocr)
        assertEquals(55, parsed.currentPercent)
        assertEquals(62, parsed.minutesToFull)
    }
}
