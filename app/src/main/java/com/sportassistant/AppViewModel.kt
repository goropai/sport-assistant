package com.sportassistant

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.sportassistant.ai.AnalysisError
import com.sportassistant.ai.AnalysisResult
import com.sportassistant.ai.FoodAnalyzer
import com.sportassistant.ai.FoodItem
import com.sportassistant.ai.GeminiClient
import com.sportassistant.ai.ObservationAnalyzer
import com.sportassistant.ai.ObservationOutcome
import com.sportassistant.ai.ObservationResult
import com.sportassistant.ai.ObservationType
import com.sportassistant.data.Observation
import kotlinx.coroutines.Dispatchers
import com.sportassistant.data.AppDatabase
import com.sportassistant.data.FoodEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.util.Locale
import com.sportassistant.data.Profile
import com.sportassistant.data.ProfileStore
import com.sportassistant.data.Stats
import com.sportassistant.data.Walk
import com.sportassistant.data.WorkoutLog
import com.sportassistant.data.computeStats
import com.sportassistant.health.HealthRepository
import com.sportassistant.health.HealthUiState
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

sealed interface FoodAnalysisState {
    data object Idle : FoodAnalysisState
    data object Analyzing : FoodAnalysisState
    data class Review(val items: List<FoodItem>) : FoodAnalysisState
    data object NoFood : FoodAnalysisState
    data class Failed(val error: AnalysisError) : FoodAnalysisState
}

sealed interface ObservationState {
    data object Idle : ObservationState
    data object Analyzing : ObservationState
    data class Result(val type: ObservationType, val result: ObservationResult, val jpeg: ByteArray) : ObservationState
    data class Failed(val error: AnalysisError) : ObservationState
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

    private val foodDao = db.foodDao()

    /** Re-subscribes at midnight: the day key is re-evaluated every minute. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val foodToday: StateFlow<List<FoodEntry>> = flow {
        while (true) {
            emit(LocalDate.now().toEpochDay())
            delay(60_000)
        }
    }.distinctUntilChanged().flatMapLatest { foodDao.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _analysis = MutableStateFlow<FoodAnalysisState>(FoodAnalysisState.Idle)
    val analysis: StateFlow<FoodAnalysisState> = _analysis.asStateFlow()

    fun analyzeFood(photo: Uri) {
        val p = profile.value ?: return
        _analysis.value = FoodAnalysisState.Analyzing
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _analysis.value = try {
                val jpeg = GeminiClient.prepareJpeg(ctx, photo)
                val lang = if (Locale.getDefault().language == "uk") "uk" else "en"
                when (val r = FoodAnalyzer.analyze(p.geminiKey, jpeg, lang)) {
                    is AnalysisResult.Success -> FoodAnalysisState.Review(r.items)
                    AnalysisResult.NoFood -> FoodAnalysisState.NoFood
                    is AnalysisResult.Failure -> FoodAnalysisState.Failed(r.error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // unreadable image, out of memory while decoding, ...
                FoodAnalysisState.Failed(AnalysisError.BAD_RESPONSE)
            }
        }
    }

    fun dismissAnalysis() {
        _analysis.value = FoodAnalysisState.Idle
    }

    fun saveFood(items: List<FoodItem>) {
        val now = System.currentTimeMillis()
        val day = LocalDate.now().toEpochDay()
        viewModelScope.launch {
            foodDao.insertAll(
                items.map { FoodEntry(epochDay = day, loggedAt = now, name = it.name, grams = it.grams, kcal = it.kcal, proteinG = it.proteinG, fatG = it.fatG, carbsG = it.carbsG) },
            )
        }
        _analysis.value = FoodAnalysisState.Idle
    }

    fun deleteFood(id: Long) {
        viewModelScope.launch { foodDao.delete(id) }
    }

    // --- Posture / skin observations ---------------------------------------------------------

    private val obsDao = db.observationDao()
    val observations: StateFlow<List<Observation>> =
        obsDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _obs = MutableStateFlow<ObservationState>(ObservationState.Idle)
    val obsState: StateFlow<ObservationState> = _obs.asStateFlow()

    fun analyzeObservation(photo: Uri, type: ObservationType) {
        val key = profile.value?.geminiKey.orEmpty()
        _obs.value = ObservationState.Analyzing
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _obs.value = try {
                val jpeg = GeminiClient.prepareJpeg(ctx, photo)
                val lang = if (Locale.getDefault().language == "uk") "uk" else "en"
                when (val r = ObservationAnalyzer.analyze(key, type, jpeg, lang)) {
                    is ObservationOutcome.Success -> ObservationState.Result(type, r.result, jpeg)
                    is ObservationOutcome.Failure -> ObservationState.Failed(r.error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ObservationState.Failed(AnalysisError.BAD_RESPONSE)
            }
        }
    }

    fun dismissObservation() {
        _obs.value = ObservationState.Idle
    }

    /** Saves the analysis; the photo is written to app-private storage only if [keepPhoto]. */
    fun saveObservation(keepPhoto: Boolean) {
        val s = _obs.value as? ObservationState.Result ?: return
        _obs.value = ObservationState.Idle
        viewModelScope.launch(Dispatchers.IO) {
            var path: String? = null
            if (keepPhoto) {
                val dir = File(getApplication<Application>().filesDir, "observations").apply { mkdirs() }
                val file = File(dir, "obs_${System.currentTimeMillis()}.jpg")
                file.writeBytes(s.jpeg)
                path = file.absolutePath
            }
            obsDao.insert(
                Observation(
                    epochDay = LocalDate.now().toEpochDay(),
                    type = s.type.name,
                    summary = s.result.summary,
                    details = s.result.observations.joinToString("\n"),
                    suggestions = s.result.suggestions.joinToString("\n"),
                    seeDoctor = s.result.seeDoctor,
                    doctorReason = s.result.doctorReason,
                    photoPath = path,
                ),
            )
        }
    }

    fun deleteObservation(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            obsDao.get(id)?.photoPath?.let { File(it).delete() }
            obsDao.delete(id)
        }
    }

    private val healthRepo = HealthRepository(app)
    val healthPermissions: Set<String> = healthRepo.permissions

    private val _health = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val health: StateFlow<HealthUiState> = _health.asStateFlow()

    fun refreshHealth() {
        viewModelScope.launch { _health.value = healthRepo.load() }
    }

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
