package com.chupacabra.evchargeestimation.domain

/**
 * Heuristic parser for EV dashboard OCR text.
 *
 * Tuned against real instrument-cluster photos (see app/sampledata/), especially:
 * - Peugeot / Stellantis charging screens:
 *     "CHARGING COMPLETE IN 3 h 25 min", "Charge of 42 km per hour", "52 %"
 * - Chevrolet Bolt–style clusters (issue photo d9735234-…):
 *     "estimated charge complete in 1 hrs 02 min", gauge "80%", "68 miles range"
 *
 * Looks for:
 * - Battery percent: "45%", "SOC 45", "45 %", sometimes labeled charge/battery values
 * - Time to full: "1h 20m", "3 h 25 min", "1 hrs 02 min", "80 min", "1:20", "COMPLETE IN …"
 *
 * Fully on-device; no network / cloud AI.
 */
object DashboardOcrParser {

    data class ParsedDashboard(
        val currentPercent: Int?,
        val minutesToFull: Int?,
        val rawText: String,
        val confidenceNotes: List<String> = emptyList()
    )

    private data class PercentCandidate(val value: Int, val score: Int)
    private data class MinuteCandidate(val minutes: Int, val specificity: Int)

    /**
     * Hour unit token: h / hr / hrs / hour / hours (Bolt uses "hrs").
     * Must not require a trailing space-only boundary before minutes digits.
     */
    private const val HOUR_UNIT = """h(?:rs?|ours?)?"""
    private const val MIN_UNIT = """m(?:in(?:utes?)?)?"""

    /** Values with an explicit % sign. */
    private val percentWithSign = Regex("""(?<!\d)(\d{1,3})\s*%""")

    /** "SOC 45", "Battery 52", "charged 40" — but not "Charge of 42 km". */
    private val percentWithLabel = Regex(
        """(?i)(?:soc|battery|charged|state\s*of\s*charge)[^\d%]{0,12}(\d{1,3})\s*%?"""
    )

    /** "52 % SOC", "45% battery" */
    private val percentTrailingLabel = Regex(
        """(?i)(\d{1,3})\s*(?:%|percent|pct)?\s*(?:soc|battery|charge\b)"""
    )

    /**
     * "CHARGING COMPLETE IN 3 h 25 min" / Bolt "estimated charge complete in 1 hrs 02 min".
     * Optional leading "charging" / "estimated charge" phrasing; OCR may glue words.
     */
    private val chargingCompleteIn = Regex(
        """(?i)(?:(?:estimated\s+)?charg(?:e|ing)\s*)?complete\s*in\s*(\d{1,2})\s*$HOUR_UNIT\s*(\d{1,2})\s*$MIN_UNIT"""
    )
    private val chargingCompleteInHoursOnly = Regex(
        // Reject only when a minute component follows ("2 h 15 min"), not "2 h  30%"
        """(?i)(?:(?:estimated\s+)?charg(?:e|ing)\s*)?complete\s*in\s*(\d{1,2})\s*$HOUR_UNIT(?!\s*\d{1,2}\s*$MIN_UNIT)"""
    )
    private val chargingCompleteInMinutesOnly = Regex(
        """(?i)(?:(?:estimated\s+)?charg(?:e|ing)\s*)?complete\s*in\s*(\d{1,3})\s*(?:min(?:utes?)?|mins)\b"""
    )

    private val hoursMinutes = Regex(
        """(?i)(\d{1,2})\s*$HOUR_UNIT\s*(?:and\s*)?(\d{1,2})\s*$MIN_UNIT"""
    )
    private val hoursOnly = Regex(
        """(?i)(\d{1,2})\s*$HOUR_UNIT(?!\s*\d{1,2}\s*$MIN_UNIT)"""
    )
    private val minutesOnlyLabeled = Regex(
        """(?i)(\d{1,3})\s*(?:min(?:utes?)?|mins)\b"""
    )
    /** Duration-like clocks; exclude wall-clock times with am/pm (e.g. "2:31 pm"). */
    private val clockStyle = Regex("""(?i)\b(\d{1,2}):(\d{2})\b(?!\s*[ap]\.?m\.?\b)""")
    private val timeNearFull = Regex(
        """(?i)(?:full|remaining|left|until|to\s*100|charge\s*time|est\.?)[^\d]{0,20}(\d{1,3})\s*(?:min|mins|minutes|h|hr|hrs|hours)?"""
    )

    /** Charge *rate* lines that must not be treated as SOC or time-to-full. */
    private val chargeRateSnippet = Regex(
        """(?i)charge\s+of\s+\d+\s*km(?:\s*per\s*hour)?|""" +
            """\d+\s*km\s*(?:/|\s*per\s*)\s*h(?:our)?|""" +
            """\d+\s*km/h"""
    )

    /** Distance / odo context — numbers next to these are not charge %. */
    private val distanceContext = Regex(
        """(?i)\b\d{1,6}\s*(?:km|mi(?:les?)?)\b(?:\s*range)?"""
    )

    /** AC voltage on charging screens (e.g. Bolt "240 V") — not a duration or SOC. */
    private val voltageContext = Regex(
        """(?i)\b\d{2,4}\s*v(?:olts?)?\b"""
    )

    /** Cabin / ambient temp (e.g. "66°F") — ignore. */
    private val temperatureContext = Regex(
        """(?i)\b\d{1,3}\s*°\s*[fc]\b|\b\d{1,3}\s*degrees?\b"""
    )

    fun parse(ocrText: String): ParsedDashboard {
        val text = normalize(ocrText)
        if (text.isEmpty()) {
            return ParsedDashboard(null, null, ocrText, listOf("No text detected"))
        }

        val notes = mutableListOf<String>()
        val scrubbed = scrubNonChargeNoise(text)
        val percent = extractPercent(scrubbed, text, notes)
        val minutes = extractMinutesToFull(scrubbed, notes, percent)

        return ParsedDashboard(
            currentPercent = percent,
            minutesToFull = minutes,
            rawText = ocrText,
            confidenceNotes = notes
        )
    }

    /**
     * Collapse whitespace and fix a few OCR quirks common on glowing dashboards.
     */
    private fun normalize(ocrText: String): String {
        return ocrText
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("""\s+"""), " ")
            // OCR sometimes splits "COMPLETE" or drops spaces around "IN"
            .replace(Regex("""(?i)complete\s*in"""), "COMPLETE IN")
            // "3h25min" / "3 h25 min" already handled by \s* in patterns; keep as-is
            .trim()
    }

    /**
     * Blank out charge-rate, distance, voltage, and temperature snippets so
     * their numbers don't compete with SOC / time-to-full.
     */
    private fun scrubNonChargeNoise(text: String): String {
        var scrubbed = text
        scrubbed = chargeRateSnippet.replace(scrubbed) { match ->
            " ".repeat(match.value.length)
        }
        // Range/odo in km or miles (Bolt: "68 miles range", "32931 mi")
        scrubbed = distanceContext.replace(scrubbed) { match ->
            " ".repeat(match.value.length)
        }
        scrubbed = voltageContext.replace(scrubbed) { match ->
            " ".repeat(match.value.length)
        }
        scrubbed = temperatureContext.replace(scrubbed) { match ->
            " ".repeat(match.value.length)
        }
        return scrubbed
    }

    private fun extractPercent(
        scrubbed: String,
        original: String,
        notes: MutableList<String>
    ): Int? {
        val candidates = mutableListOf<PercentCandidate>()

        // Highest trust: explicit percent sign (prefer scrubbed, fall back to original)
        percentWithSign.findAll(scrubbed).forEach { match ->
            match.groupValues[1].toIntOrNull()?.let {
                if (it in 0..100) candidates += PercentCandidate(it, score = 100)
            }
        }
        if (candidates.none { it.score >= 100 }) {
            percentWithSign.findAll(original).forEach { match ->
                match.groupValues[1].toIntOrNull()?.let {
                    if (it in 0..100) candidates += PercentCandidate(it, score = 95)
                }
            }
        }

        percentWithLabel.findAll(scrubbed).forEach { match ->
            match.groupValues[1].toIntOrNull()?.let {
                if (it in 0..100) candidates += PercentCandidate(it, score = 70)
            }
        }
        percentTrailingLabel.findAll(scrubbed).forEach { match ->
            // Avoid matching the word "charge" inside "charging complete"
            val whole = match.value.lowercase()
            if (whole.contains("charging")) return@forEach
            match.groupValues[1].toIntOrNull()?.let {
                if (it in 0..100) candidates += PercentCandidate(it, score = 60)
            }
        }

        return when {
            candidates.isEmpty() -> {
                notes += "No charge percentage found"
                null
            }
            else -> {
                // Prefer higher score; among ties, prefer values nearer mid-range
                // only as a weak tie-break (real SOCs are often mid while odo is large).
                val chosen = candidates
                    .groupBy { it.value }
                    .map { (value, list) ->
                        PercentCandidate(value, list.maxOf { it.score } + list.size)
                    }
                    .maxWithOrNull(
                        compareBy<PercentCandidate> { it.score }
                            .thenBy { -kotlin.math.abs(it.value - 50) }
                    )
                    ?.value
                    ?: candidates.first().value
                notes += "Charge: $chosen%"
                chosen
            }
        }
    }

    private fun extractMinutesToFull(
        text: String,
        notes: MutableList<String>,
        percent: Int?
    ): Int? {
        val candidates = mutableListOf<MinuteCandidate>()

        // Highest trust: explicit "CHARGING COMPLETE IN …" (sampledata dashboards)
        chargingCompleteIn.findAll(text).forEach { match ->
            val h = match.groupValues[1].toIntOrNull() ?: return@forEach
            val m = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (h in 0..24 && m in 0..59) {
                candidates += MinuteCandidate(h * 60 + m, specificity = 200)
            }
        }
        chargingCompleteInHoursOnly.findAll(text).forEach { match ->
            val h = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (h in 1..24) {
                candidates += MinuteCandidate(h * 60, specificity = 180)
            }
        }
        chargingCompleteInMinutesOnly.findAll(text).forEach { match ->
            val m = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (m in 1..24 * 60) {
                candidates += MinuteCandidate(m, specificity = 180)
            }
        }

        hoursMinutes.findAll(text).forEach { match ->
            val h = match.groupValues[1].toIntOrNull() ?: return@forEach
            val m = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (h in 0..24 && m in 0..59) {
                candidates += MinuteCandidate(h * 60 + m, specificity = 100)
            }
        }

        clockStyle.findAll(text).forEach { match ->
            val h = match.groupValues[1].toIntOrNull() ?: return@forEach
            val m = match.groupValues[2].toIntOrNull() ?: return@forEach
            // Prefer duration-like clocks (0:xx … ~12:xx), not wall times
            if (h in 0..12 && m in 0..59) {
                candidates += MinuteCandidate(h * 60 + m, specificity = 90)
            }
        }

        minutesOnlyLabeled.findAll(text).forEach { match ->
            val m = match.groupValues[1].toIntOrNull() ?: return@forEach
            // If this minute token is already part of an "Xh Ym" match, hoursMinutes
            // / chargingCompleteIn will outrank it via higher specificity.
            if (m in 1..24 * 60) {
                candidates += MinuteCandidate(m, specificity = 80)
            }
        }

        hoursOnly.findAll(text).forEach { match ->
            val h = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (h in 1..24) {
                candidates += MinuteCandidate(h * 60, specificity = 50)
            }
        }

        timeNearFull.findAll(text).forEach { match ->
            val n = match.groupValues[1].toIntOrNull() ?: return@forEach
            val snippet = match.value.lowercase()
            when {
                snippet.contains("h") && !snippet.contains("min") && n in 1..24 ->
                    candidates += MinuteCandidate(n * 60, specificity = 40)
                n in 1..24 * 60 ->
                    candidates += MinuteCandidate(n, specificity = 40)
            }
        }

        // Drop candidates that are almost certainly the SOC misread as minutes
        val filtered = candidates.filter { candidate ->
            percent == null || candidate.minutes != percent || candidate.specificity >= 100
        }

        return when {
            filtered.isEmpty() -> {
                notes += "No time-to-full found"
                null
            }
            else -> {
                val chosen = filtered
                    .sortedWith(
                        compareByDescending<MinuteCandidate> { it.specificity }
                            .thenBy { v ->
                                when {
                                    v.minutes in 5..600 -> 0
                                    v.minutes in 1..24 * 60 -> 1
                                    else -> 2
                                }
                            }
                    )
                    .first()
                    .minutes
                notes += "Time to full: ${ChargeEstimator.formatDuration(chosen)}"
                chosen
            }
        }
    }
}
