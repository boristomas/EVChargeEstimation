package com.chupacabra.evchargeestimation.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chupacabra.evchargeestimation.data.ChargeHistoryEntry
import com.chupacabra.evchargeestimation.data.ChargeHistoryRepository
import com.chupacabra.evchargeestimation.domain.ChargeEstimator
import com.chupacabra.evchargeestimation.domain.DashboardOcrParser
import com.chupacabra.evchargeestimation.update.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

data class AppUpdateUiState(
    val checking: Boolean = false,
    val available: AppUpdateChecker.AvailableUpdate? = null,
    val downloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadedApk: File? = null,
    val message: String? = null,
    val dismissed: Boolean = false
)

class ChargeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChargeHistoryRepository.get(application)
    private val updateChecker = AppUpdateChecker(application.applicationContext)

    private val _ui = MutableStateFlow(CalculatorUiState())
    val ui: StateFlow<CalculatorUiState> = _ui.asStateFlow()

    private val _update = MutableStateFlow(AppUpdateUiState())
    val update: StateFlow<AppUpdateUiState> = _update.asStateFlow()

    val history: StateFlow<List<ChargeHistoryEntry>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var saveJob: Job? = null
    private var lastSavedSignature: String? = null
    private var updateJob: Job? = null

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

    /** Quiet check on launch (no error banner if offline). */
    fun checkForUpdatesOnLaunch() {
        checkForUpdates(silent = true)
    }

    fun checkForUpdates(silent: Boolean = false) {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            _update.update {
                it.copy(checking = true, message = if (silent) it.message else null)
            }
            when (val result = updateChecker.checkForUpdate()) {
                is AppUpdateChecker.CheckResult.UpdateAvailable -> {
                    _update.update {
                        it.copy(
                            checking = false,
                            available = result.update,
                            downloadedApk = null,
                            downloadProgress = 0,
                            dismissed = false,
                            message = null
                        )
                    }
                }
                AppUpdateChecker.CheckResult.UpToDate -> {
                    _update.update {
                        it.copy(
                            checking = false,
                            available = null,
                            message = if (silent) null else "You’re on the latest version"
                        )
                    }
                }
                is AppUpdateChecker.CheckResult.Error -> {
                    _update.update {
                        it.copy(
                            checking = false,
                            message = if (silent) null else result.message
                        )
                    }
                }
            }
        }
    }

    fun dismissUpdateBanner() {
        _update.update { it.copy(dismissed = true) }
    }

    fun clearUpdateMessage() {
        _update.update { it.copy(message = null) }
    }

    fun downloadUpdate() {
        val update = _update.value.available ?: return
        if (_update.value.downloading) return
        viewModelScope.launch {
            _update.update {
                it.copy(downloading = true, downloadProgress = 0, message = null)
            }
            try {
                val file = withContext(Dispatchers.IO) {
                    updateChecker.downloadApk(update) { pct ->
                        _update.update { state -> state.copy(downloadProgress = pct) }
                    }
                }
                _update.update {
                    it.copy(
                        downloading = false,
                        downloadProgress = 100,
                        downloadedApk = file,
                        message = "Download ready — tap Install"
                    )
                }
            } catch (e: Exception) {
                _update.update {
                    it.copy(
                        downloading = false,
                        message = e.message ?: "Download failed"
                    )
                }
            }
        }
    }

    /**
     * @return true if install was started; false if the user must allow install permission first
     */
    fun installDownloadedUpdate(): Boolean {
        val file = _update.value.downloadedApk ?: return false
        if (!updateChecker.canRequestInstalls()) {
            getApplication<Application>().startActivity(
                updateChecker.installPermissionSettingsIntent()
            )
            _update.update {
                it.copy(message = "Allow installing apps from EV Charge, then tap Install again")
            }
            return false
        }
        return try {
            updateChecker.installApk(file)
            true
        } catch (e: Exception) {
            _update.update { it.copy(message = e.message ?: "Could not open installer") }
            false
        }
    }

    fun openReleasePage() {
        val update = _update.value.available ?: return
        try {
            updateChecker.openReleasePage(update)
        } catch (_: Exception) {
            _update.update { it.copy(message = "Could not open the download page") }
        }
    }

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
