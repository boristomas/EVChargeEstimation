package com.chupacabra.evchargeestimation.domain

/**
 * Linear charge-time estimator.
 *
 * Given the car's reported minutes-to-100% at the current SOC, scales that
 * remaining time to the gap between [currentPercent] and [desiredPercent].
 *
 * minutes = minutesToFull * (desired - current) / (100 - current)
 */
object ChargeEstimator {

    data class Result(
        val minutesToDesired: Int,
        val hours: Int,
        val remainingMinutes: Int,
        val isAlreadyThere: Boolean,
        val message: String? = null
    )

    data class Input(
        val currentPercent: Int,
        val minutesToFull: Int,
        val desiredPercent: Int
    )

    fun estimate(input: Input): Result {
        val current = input.currentPercent
        val toFull = input.minutesToFull
        val desired = input.desiredPercent

        when {
            current !in 0..100 -> {
                return Result(0, 0, 0, false, "Current charge must be 0–100%")
            }
            desired !in 0..100 -> {
                return Result(0, 0, 0, false, "Desired charge must be 0–100%")
            }
            toFull < 0 -> {
                return Result(0, 0, 0, false, "Minutes to full cannot be negative")
            }
            desired <= current -> {
                return Result(
                    minutesToDesired = 0,
                    hours = 0,
                    remainingMinutes = 0,
                    isAlreadyThere = true,
                    message = "Already at or above the desired charge"
                )
            }
            current >= 100 -> {
                return Result(
                    minutesToDesired = 0,
                    hours = 0,
                    remainingMinutes = 0,
                    isAlreadyThere = true,
                    message = "Battery is already full"
                )
            }
            toFull == 0 && desired > current -> {
                return Result(0, 0, 0, false, "Minutes to full is 0 but target is higher than current")
            }
        }

        val remainingToFull = 100 - current
        val percentNeeded = desired - current
        val rawMinutes = toFull.toDouble() * percentNeeded / remainingToFull
        val minutes = rawMinutes.coerceAtLeast(0.0).let {
            // Round half up so short windows are not shown as 0 when still charging
            kotlin.math.round(it).toInt()
        }

        return Result(
            minutesToDesired = minutes,
            hours = minutes / 60,
            remainingMinutes = minutes % 60,
            isAlreadyThere = false
        )
    }

    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "0 min"
        val (h, m) = splitMinutes(minutes)
        return when {
            h == 0 -> "$m min"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** Total minutes from separate hour + minute fields (car-style). */
    fun totalMinutes(hours: Int, minutes: Int): Int =
        (hours.coerceAtLeast(0) * 60) + minutes.coerceAtLeast(0)

    /** Split total minutes into hours + remaining minutes (e.g. 120 → 2h 0m). */
    fun splitMinutes(totalMinutes: Int): Pair<Int, Int> {
        val safe = totalMinutes.coerceAtLeast(0)
        return safe / 60 to safe % 60
    }
}
