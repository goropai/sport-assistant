package com.sportassistant

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sportassistant.data.AppDatabase
import com.sportassistant.data.Profile
import com.sportassistant.data.ProfileStore
import com.sportassistant.data.Stats
import com.sportassistant.data.Walk
import com.sportassistant.data.WorkoutLog
import com.sportassistant.data.computeStats
import com.sportassistant.reminders.Reminders
import com.sportassistant.track.TrackRecorder
import com.sportassistant.track.TrackState
import com.sportassistant.track.WalkMath
import com.sportassistant.track.WalkService
import com.sportassistant.update.HashMismatchException
import com.sportassistant.update.UpdateInfo
import com.sportassistant.update.Updater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

const val POINTS_PER_EXERCISE = 10
const val POINTS_PLAN_BONUS = 20
const val POINTS_PER_KM = 20
const val MIN_WALK_M = 100.0

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object NotConfigured : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object Downloading : UpdateState
    data class NeedsInstallPermission(val info: UpdateInfo) : UpdateState
    data object HashMismatch : UpdateState
    data object Error : UpdateState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore(app)
    private val db = AppDatabase.get(app)
    private val dao = db.logDao()
    private val walkDao = db.walkDao()

    /** Null until the first read from disk completes. */
    val profile: StateFlow<Profile?> =
        store.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val stats: StateFlow<Stats> = dao.observeAll()
        .map { computeStats(it, LocalDate.now().toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, computeStats(emptyList(), LocalDate.now().toEpochDay()))

    val walks: StateFlow<List<Walk>> =
        walkDao.observeRecent().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val track: StateFlow<TrackState> = TrackRecorder.state

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    fun saveProfile(p: Profile) {
        viewModelScope.launch {
            store.save(p)
            Reminders.sync(getApplication(), p)
        }
    }

    /** Records a completed plan and returns the awarded points. */
    fun logWorkout(exercises: Int, seconds: Int): Int {
        val points = exercises * POINTS_PER_EXERCISE + POINTS_PLAN_BONUS
        viewModelScope.launch {
            dao.insert(WorkoutLog(epochDay = LocalDate.now().toEpochDay(), exercises = exercises, seconds = seconds, points = points))
        }
        return points
    }

    fun startWalk() = WalkService.begin(getApplication())

    /** Stops tracking and saves the walk. Returns false if it was too short to keep. */
    fun finishWalk(): Boolean {
        val result = TrackRecorder.stop(SystemClock.elapsedRealtime())
        WalkService.end(getApplication())
        if (result.distanceM < MIN_WALK_M) return false

        val weight = profile.value?.weightKg ?: Profile().weightKg
        val kcal = WalkMath.kcal(result.distanceM, result.durationSec, weight)
        val points = (result.distanceM / 1000 * POINTS_PER_KM).toInt()
        val today = LocalDate.now().toEpochDay()
        viewModelScope.launch {
            walkDao.insert(
                Walk(
                    epochDay = today,
                    durationSec = result.durationSec,
                    distanceM = result.distanceM,
                    kcal = kcal,
                    path = result.points.joinToString(";") { "${it.lat},${it.lon}" },
                ),
            )
            // Counts toward streak and points like any other workout.
            dao.insert(WorkoutLog(epochDay = today, exercises = 0, seconds = result.durationSec.toInt(), points = points))
        }
        return true
    }

    fun checkForUpdate() {
        val url = BuildConfig.UPDATE_URL
        if (url.isBlank()) {
            _update.value = UpdateState.NotConfigured
            return
        }
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = try {
                val info = Updater.fetch(url)
                if (info.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                UpdateState.Error
            }
        }
    }

    fun installUpdate(info: UpdateInfo) {
        _update.value = UpdateState.Downloading
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _update.value = try {
                val apk: File = Updater.download(ctx, info)
                if (Updater.install(ctx, apk)) UpdateState.Idle else UpdateState.NeedsInstallPermission(info)
            } catch (e: HashMismatchException) {
                UpdateState.HashMismatch
            } catch (e: Exception) {
                UpdateState.Error
            }
        }
    }
}
