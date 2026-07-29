package com.chupacabra.evchargeestimation.ocr

import com.chupacabra.evchargeestimation.domain.DashboardOcrParser

/**
 * Rolling-window vote over continuous OCR frames for stable dashboard readings.
 * Mode (most frequent) wins; ties keep the previous consensus when possible.
 */
class OcrConsensus(
    private val windowSize: Int = DEFAULT_WINDOW
) {
    private val percentVotes = ArrayDeque<Int>(windowSize)
    private val minutesVotes = ArrayDeque<Int>(windowSize)

    var consensusPercent: Int? = null
        private set
    var consensusMinutes: Int? = null
        private set
    var framesProcessed: Int = 0
        private set
    var framesWithSignal: Int = 0
        private set

    fun add(parsed: DashboardOcrParser.ParsedDashboard) {
        framesProcessed++
        val hasSignal = parsed.currentPercent != null || parsed.minutesToFull != null
        if (!hasSignal) return
        framesWithSignal++

        parsed.currentPercent?.let { push(percentVotes, it) }
        parsed.minutesToFull?.let { push(minutesVotes, it) }

        consensusPercent = mode(percentVotes) ?: consensusPercent
        consensusMinutes = mode(minutesVotes) ?: consensusMinutes
    }

    fun toParsed(rawText: String = ""): DashboardOcrParser.ParsedDashboard {
        val notes = buildList {
            consensusPercent?.let { add("Charge: $it%") }
            consensusMinutes?.let {
                add(
                    "Time to full: ${
                        com.chupacabra.evchargeestimation.domain.ChargeEstimator.formatDuration(it)
                    }"
                )
            }
            add("Frames: $framesWithSignal/$framesProcessed")
        }
        return DashboardOcrParser.ParsedDashboard(
            currentPercent = consensusPercent,
            minutesToFull = consensusMinutes,
            rawText = rawText,
            confidenceNotes = notes
        )
    }

    fun hasConfirmableReading(): Boolean {
        val pOk = consensusPercent != null && percentVotes.size >= MIN_VOTES
        val mOk = consensusMinutes != null && minutesVotes.size >= MIN_VOTES
        // Confirm if we have at least one solid field (user can edit the rest)
        return pOk || mOk
    }

    fun reset() {
        percentVotes.clear()
        minutesVotes.clear()
        consensusPercent = null
        consensusMinutes = null
        framesProcessed = 0
        framesWithSignal = 0
    }

    private fun push(deque: ArrayDeque<Int>, value: Int) {
        if (deque.size >= windowSize) deque.removeFirst()
        deque.addLast(value)
    }

    private fun mode(values: Collection<Int>): Int? {
        if (values.isEmpty()) return null
        return values
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy({ it.value }, { -kotlin.math.abs(it.key - 50) }))
            ?.key
    }

    companion object {
        const val DEFAULT_WINDOW = 12
        const val MIN_VOTES = 2
    }
}
