package com.sportassistant.track

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LatLon(val lat: Double, val lon: Double)

data class TrackState(
    val running: Boolean = false,
    /** SystemClock.elapsedRealtime() at start. */
    val startMs: Long = 0,
    val points: List<LatLon> = emptyList(),
    val distanceM: Double = 0.0,
    /** True once at least one usable GPS fix arrived. */
    val hasFix: Boolean = false,
)

data class FinishedTrack(val durationSec: Long, val points: List<LatLon>, val distanceM: Double)

/**
 * In-process holder of the walk in progress. [WalkService] feeds it, the UI observes it.
 * Both live in the same process, so a plain singleton is enough.
 */
object TrackRecorder {
    private val _state = MutableStateFlow(TrackState())
    val state: StateFlow<TrackState> = _state.asStateFlow()

    private var lastFixMs = 0L

    fun start(nowMs: Long) {
        if (_state.value.running) return
        lastFixMs = 0L
        _state.value = TrackState(running = true, startMs = nowMs)
    }

    fun addFix(lat: Double, lon: Double, accuracyM: Float, timeMs: Long) {
        val s = _state.value
        if (!s.running) return
        val last = s.points.lastOrNull()
        val step = last?.let { WalkMath.distanceM(it.lat, it.lon, lat, lon) }
        val dt = if (last != null) (timeMs - lastFixMs) / 1000.0 else null
        if (!WalkMath.accept(accuracyM, step, dt)) return
        lastFixMs = timeMs
        _state.value = s.copy(
            points = s.points + LatLon(lat, lon),
            distanceM = s.distanceM + (step ?: 0.0),
            hasFix = true,
        )
    }

    /** Ends the walk and returns what was recorded. */
    fun stop(nowMs: Long): FinishedTrack {
        val s = _state.value
        _state.value = TrackState()
        return FinishedTrack(((nowMs - s.startMs) / 1000).coerceAtLeast(0), s.points, s.distanceM)
    }
}
