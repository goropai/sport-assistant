package com.sportassistant.track

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure functions for GPS tracking, kept free of Android types so they are unit-testable. */
object WalkMath {
    const val MAX_ACCURACY_M = 30f
    const val MIN_STEP_M = 4.0
    const val MAX_SPEED_MS = 12.0 // ~43 km/h: anything faster on foot is a GPS jump

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /**
     * Decides whether a GPS fix should extend the track. [stepM] and [dtSec] describe the move
     * from the last accepted fix (null for the very first fix).
     */
    fun accept(accuracyM: Float, stepM: Double?, dtSec: Double?): Boolean {
        if (accuracyM > MAX_ACCURACY_M) return false
        if (stepM == null || dtSec == null) return true
        if (stepM < MIN_STEP_M) return false // standing still: drift, not movement
        if (dtSec > 0 && stepM / dtSec > MAX_SPEED_MS) return false
        return true
    }

    /** MET values from the Compendium of Physical Activities (walking/running by speed). */
    fun met(speedKmh: Double): Double = when {
        speedKmh < 3.2 -> 2.5
        speedKmh < 4.8 -> 3.0
        speedKmh < 5.6 -> 3.5
        speedKmh < 6.4 -> 4.3
        speedKmh < 8.0 -> 5.0
        else -> 8.3
    }

    fun kcal(distanceM: Double, durationSec: Long, weightKg: Int): Int {
        if (durationSec <= 0) return 0
        val hours = durationSec / 3600.0
        val speedKmh = distanceM / 1000.0 / hours
        return (met(speedKmh) * weightKg * hours).toInt()
    }

    /** Seconds per kilometre, or null if there is not enough movement. */
    fun paceSecPerKm(distanceM: Double, durationSec: Long): Int? =
        if (distanceM < 50 || durationSec <= 0) null else (durationSec / (distanceM / 1000.0)).toInt()
}
