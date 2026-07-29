package com.chupacabra.evchargeestimation.data

/**
 * One saved estimation, stored locally on device.
 */
data class ChargeHistoryEntry(
    val id: String,
    val timestampMillis: Long,
    val currentPercent: Int,
    val minutesToFull: Int,
    val desiredPercent: Int,
    val resultMinutes: Int,
    val source: String // "manual" or "ocr"
)
