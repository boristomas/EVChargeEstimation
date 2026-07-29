package com.chupacabra.evchargeestimation.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Offline history backed by SharedPreferences + JSON.
 * No server, database process, or network required.
 */
class ChargeHistoryRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<ChargeHistoryEntry>>() {}.type

    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<ChargeHistoryEntry>> = _history.asStateFlow()

    fun add(
        currentPercent: Int,
        minutesToFull: Int,
        desiredPercent: Int,
        resultMinutes: Int,
        source: String
    ): ChargeHistoryEntry {
        val entry = ChargeHistoryEntry(
            id = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            currentPercent = currentPercent,
            minutesToFull = minutesToFull,
            desiredPercent = desiredPercent,
            resultMinutes = resultMinutes,
            source = source
        )
        val updated = listOf(entry) + _history.value
        // Cap log size for lightweight storage
        val capped = updated.take(MAX_ENTRIES)
        _history.value = capped
        persist(capped)
        return entry
    }

    fun clear() {
        _history.value = emptyList()
        persist(emptyList())
    }

    fun delete(id: String) {
        val updated = _history.value.filterNot { it.id == id }
        _history.value = updated
        persist(updated)
    }

    private fun load(): List<ChargeHistoryEntry> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            gson.fromJson<List<ChargeHistoryEntry>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(entries: List<ChargeHistoryEntry>) {
        prefs.edit().putString(KEY_HISTORY, gson.toJson(entries)).apply()
    }

    companion object {
        private const val PREFS_NAME = "ev_charge_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_ENTRIES = 200

        @Volatile
        private var instance: ChargeHistoryRepository? = null

        fun get(context: Context): ChargeHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: ChargeHistoryRepository(context).also { instance = it }
            }
        }
    }
}
