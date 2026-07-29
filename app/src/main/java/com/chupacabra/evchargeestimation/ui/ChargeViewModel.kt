package com.chupacabra.evchargeestimation.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chupacabra.evchargeestimation.data.ChargeHistoryEntry
import com.chupacabra.evchargeestimation.data.ChargeHistoryRepository
import com.chupacabra.evchargeestimation.domain.ChargeEstimator
import com.chupacabra.evchargeestimation.domain.DashboardOcrParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalculatorUiState(
    val currentPercent: String = "",
    /** Hours component of car "time to full" (e.g. 3 in "3 h 25 min"). */
    val hoursToFull: String = "",
    /** Minutes component 0–59 (auto-normalizes if user types ≥ 60). */
    val minutesPartToFull: String = "",
    val desiredPercent: String = "80",
    val resultMinutes: Int? = null,
    val resultLabel: String = "",
    val error: String? = null,
    val lastSource: String = "manual",
    /** Friendly summary of time-to-full, e.g. "3h 25m". */
    val timeToFullLabel: String = ""
) {
    val totalMinutesToFull: Int?
        get() {
            if (hoursToFull.isBlank() && minutesPartToFull.isBlank()) return null
            val h = hoursToFull.toIntOrNull() ?: 0
            val m = minutesPartToFull.toIntOrNull() ?: 0
            return ChargeEstimator.totalMinutes(h, m)
        }
}

class ChargeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChargeHistoryRepository.get(application)

    private val _ui = MutableStateFlow(CalculatorUiState())
    val ui: StateFlow<CalculatorUiState> = _ui.asStateFlow()

    val history: StateFlow<List<ChargeHistoryEntry>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var saveJob: Job? = null
    private var lastSavedSignature: String? = null

    fun onCurrentPercentChange(value: String) {
        _ui.update {
            it.copy(
                currentPercent = filterInt(value, 3),
                lastSource = "manual"
            )
        }
        recalculateLive()
    }

    fun onHoursToFullChange(value: String) {
        _ui.update {
            it.copy(
                hoursToFull = filterInt(value, 3),
                lastSource = "manual"
            ).withTimeLabel()
        }
        recalculateLive()
    }

    /**
     * Minutes field (0–59). If the user types 60+ (e.g. 120), treat it as a total
     * duration and display as hours + minutes (120 → 2h 0m).
     */
    fun onMinutesPartToFullChange(value: String) {
        val digits = filterInt(value, 4)
        val asInt = digits.toIntOrNull()
        _ui.update { state ->
            if (asInt != null && asInt >= 60) {
                val (h, m) = ChargeEstimator.splitMinutes(asInt)
                state.copy(
                    hoursToFull = h.toString(),
                    minutesPartToFull = m.toString(),
                    lastSource = "manual"
                ).withTimeLabel()
            } else {
                state.copy(
                    minutesPartToFull = digits.take(2),
                    lastSource = "manual"
                ).withTimeLabel()
            }
        }
        recalculateLive()
    }

    fun onDesiredPercentChange(value: String) {
        _ui.update {
            it.copy(
                desiredPercent = filterInt(value, 3),
                lastSource = "manual"
            )
        }
        recalculateLive()
    }

    fun applyOcrResult(parsed: DashboardOcrParser.ParsedDashboard) {
        _ui.update { state ->
            val totalMin = parsed.minutesToFull
            val (h, m) = if (totalMin != null) {
                ChargeEstimator.splitMinutes(totalMin)
            } else {
                null to null
            }
            state.copy(
                currentPercent = parsed.currentPercent?.toString() ?: state.currentPercent,
                hoursToFull = when {
                    h != null -> h.toString()
                    else -> state.hoursToFull
                },
                minutesPartToFull = when {
                    m != null -> m.toString()
                    totalMin != null -> "0"
                    else -> state.minutesPartToFull
                },
                lastSource = "ocr",
                error = when {
                    parsed.currentPercent == null && parsed.minutesToFull == null ->
                        "Couldn’t read the car screen. Try scanning again or type the numbers."
                    parsed.currentPercent == null ->
                        "Battery % wasn’t found — type it in."
                    parsed.minutesToFull == null ->
                        "Time to full wasn’t found — type it in."
                    else -> null
                }
            ).withTimeLabel()
        }
        recalculateLive(saveImmediately = true)
    }

    fun clearInputs() {
        saveJob?.cancel()
        _ui.value = CalculatorUiState(
            desiredPercent = _ui.value.desiredPercent.ifBlank { "80" }
        )
        recalculateLive(saveImmediately = false)
    }

    fun clearHistory() = repository.clear()

    fun deleteHistoryEntry(id: String) = repository.delete(id)

    /**
     * Recompute estimate whenever inputs change. History is saved either immediately
     * (OCR confirm) or debounced after typing so we don't flood the log.
     */
    private fun recalculateLive(saveImmediately: Boolean = false) {
        val state = _ui.value
        val current = state.currentPercent.toIntOrNull()
        val minutes = state.totalMinutesToFull
        val desired = state.desiredPercent.toIntOrNull()

        if (current == null || minutes == null || desired == null) {
            _ui.update {
                it.copy(
                    resultMinutes = null,
                    resultLabel = "",
                    error = null
                )
            }
            return
        }

        // Incomplete typing (blank hours+minutes already handled); reject empty total only if both blank
        if (state.hoursToFull.isBlank() && state.minutesPartToFull.isBlank()) {
            _ui.update { it.copy(resultMinutes = null, resultLabel = "", error = null) }
            return
        }

        val result = ChargeEstimator.estimate(
            ChargeEstimator.Input(
                currentPercent = current,
                minutesToFull = minutes,
                desiredPercent = desired
            )
        )

        if (result.message != null && result.minutesToDesired == 0 && !result.isAlreadyThere) {
            _ui.update {
                it.copy(
                    error = result.message,
                    resultMinutes = null,
                    resultLabel = ""
                )
            }
            return
        }

        val label = when {
            result.isAlreadyThere -> result.message ?: "Ready"
            else -> ChargeEstimator.formatDuration(result.minutesToDesired)
        }

        _ui.update {
            it.copy(
                resultMinutes = result.minutesToDesired,
                resultLabel = label,
                error = if (result.isAlreadyThere) result.message else null
            )
        }

        if (result.isAlreadyThere) return

        if (saveImmediately) {
            saveJob?.cancel()
            persistIfNew(current, minutes, desired, result.minutesToDesired, state.lastSource)
        } else {
            scheduleDebouncedSave(current, minutes, desired, result.minutesToDesired, state.lastSource)
        }
    }

    private fun scheduleDebouncedSave(
        current: Int,
        minutes: Int,
        desired: Int,
        resultMinutes: Int,
        source: String
    ) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(HISTORY_SAVE_DEBOUNCE_MS)
            persistIfNew(current, minutes, desired, resultMinutes, source)
            // After a manual edit is logged, treat further tweaks as manual
            _ui.update { it.copy(lastSource = "manual") }
        }
    }

    private fun persistIfNew(
        current: Int,
        minutes: Int,
        desired: Int,
        resultMinutes: Int,
        source: String
    ) {
        val signature = "$current|$minutes|$desired|$resultMinutes"
        if (signature == lastSavedSignature) return
        lastSavedSignature = signature
        repository.add(
            currentPercent = current,
            minutesToFull = minutes,
            desiredPercent = desired,
            resultMinutes = resultMinutes,
            source = source
        )
        if (source == "ocr") {
            _ui.update { it.copy(lastSource = "manual") }
        }
    }

    private fun CalculatorUiState.withTimeLabel(): CalculatorUiState {
        val total = totalMinutesToFull
        return copy(
            timeToFullLabel = when {
                hoursToFull.isBlank() && minutesPartToFull.isBlank() -> ""
                total == null -> ""
                else -> ChargeEstimator.formatDuration(total)
            }
        )
    }

    private fun filterInt(value: String, maxDigits: Int): String {
        return value.filter { it.isDigit() }.take(maxDigits)
    }

    companion object {
        private const val HISTORY_SAVE_DEBOUNCE_MS = 1_500L
    }
}
