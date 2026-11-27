package com.sportassistant

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import androidx.room.withTransaction
import com.sportassistant.backup.BackupCodec
import com.sportassistant.backup.BackupData
import com.sportassistant.backup.BackupError
import com.sportassistant.backup.BackupException
import java.io.IOException
import com.sportassistant.ai.AnalysisError
import com.sportassistant.ai.AnalysisResult
import com.sportassistant.ai.FoodAnalyzer
import com.sportassistant.ai.FoodItem
import com.sportassistant.ai.GeminiClient
import com.sportassistant.ai.NutritionLookup
import com.sportassistant.data.Per100
import com.sportassistant.ai.ObservationAnalyzer
import com.sportassistant.ai.ObservationOutcome
import com.sportassistant.ai.ObservationResult
import com.sportassistant.ai.ObservationType
import com.sportassistant.data.Observation
import kotlinx.coroutines.Dispatchers
import com.sportassistant.data.AppDatabase
import com.sportassistant.data.DayHealth
import com.sportassistant.data.FoodEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

enum class BackupFailure { READ, WRITE, NOT_A_BACKUP, NEWER_VERSION, CORRUPT, TOO_LARGE }

sealed interface BackupState {
    data object Idle : BackupState
    data object Working : BackupState
    data object Exported : BackupState
    data class Preview(val data: BackupData) : BackupState
    data object Restored : BackupState
    data class Failed(val reason: BackupFailure) : BackupState
}

sealed interface EstimateState {
    data object Idle : EstimateState
    data object Loading : EstimateState
    data class Done(val per100: Per100) : EstimateState
    data object NotFound : EstimateState
    data class Failed(val error: AnalysisError) : EstimateState
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

    init {
        // Reminders are a chain of one-off jobs; re-arming it on every launch keeps it alive even if the
        // system dropped a job (force stop, aggressive battery savers).
        viewModelScope.launch { Reminders.sync(getApplication(), store.profile.first()) }
    }

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

    private val _estimate = MutableStateFlow<EstimateState>(EstimateState.Idle)
    val estimate: StateFlow<EstimateState> = _estimate.asStateFlow()

    /** Asks Gemini for per-100 g nutrition of a food that is not in the offline table. */
    fun estimateNutrition(name: String) {
        val key = profile.value?.geminiKey.orEmpty()
        if (key.isBlank() || name.isBlank()) return
        _estimate.value = EstimateState.Loading
        viewModelScope.launch {
            val lang = if (Locale.getDefault().language == "uk") "uk" else "en"
            _estimate.value = try {
                when (val r = NutritionLookup.lookup(key, name, lang)) {
                    is AnalysisResult.Success -> {
                        val item = r.items.first()
                        // The prompt asks for 100 g, but normalise in case the model returned another weight.
                        val k = if (item.grams > 0) 100.0 / item.grams else 1.0
                        EstimateState.Done(Per100(item.kcal * k, item.proteinG * k, item.fatG * k, item.carbsG * k))
                    }
                    AnalysisResult.NoFood -> EstimateState.NotFound
                    is AnalysisResult.Failure -> EstimateState.Failed(r.error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EstimateState.Failed(AnalysisError.BAD_RESPONSE)
            }
        }
    }

    fun clearEstimate() {
        _estimate.value = EstimateState.Idle
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

    // --- Backup and restore ------------------------------------------------------------------

    private val _backup = MutableStateFlow<BackupState>(BackupState.Idle)
    val backup: StateFlow<BackupState> = _backup.asStateFlow()

    fun dismissBackup() {
        _backup.value = BackupState.Idle
    }

    /** Writes every record to [uri], a file the user picked (Downloads, Drive, ...). */
    fun exportTo(uri: Uri) {
        _backup.value = BackupState.Working
        viewModelScope.launch(Dispatchers.IO) {
            _backup.value = try {
                val data = BackupData(
                    profile = profile.value ?: Profile(),
                    logs = dao.all(),
                    walks = walkDao.all(),
                    food = foodDao.all(),
                    observations = obsDao.all(),
                )
                val text = BackupCodec.encode(data, java.time.LocalDateTime.now().toString())
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                    ?: throw IOException("cannot open the destination")
                BackupState.Exported
            } catch (e: IOException) {
                BackupState.Failed(BackupFailure.WRITE)
            }
        }
    }

    /** Reads and validates a backup file, then waits for the user to confirm before touching any data. */
    fun readBackup(uri: Uri) {
        _backup.value = BackupState.Working
        viewModelScope.launch(Dispatchers.IO) {
            _backup.value = try {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    // Stop one byte past the limit: enough to know the file is too large without loading it all.
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8 * 1024)
                    while (out.size() <= MAX_BACKUP_BYTES) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                } ?: throw IOException("cannot open the file")
                if (bytes.size > MAX_BACKUP_BYTES) {
                    BackupState.Failed(BackupFailure.TOO_LARGE)
                } else {
                    BackupState.Preview(BackupCodec.decode(String(bytes)))
                }
            } catch (e: BackupException) {
                BackupState.Failed(
                    when (e.error) {
                        BackupError.NOT_A_BACKUP -> BackupFailure.NOT_A_BACKUP
                        BackupError.NEWER_VERSION -> BackupFailure.NEWER_VERSION
                        BackupError.CORRUPT -> BackupFailure.CORRUPT
                    },
                )
            } catch (e: IOException) {
                BackupState.Failed(BackupFailure.READ)
            }
        }
    }

    /** Replaces everything on this phone with the previewed backup, in one transaction. */
    fun confirmRestore() {
        val preview = _backup.value as? BackupState.Preview ?: return
        _backup.value = BackupState.Working
        viewModelScope.launch(Dispatchers.IO) {
            _backup.value = try {
                val oldPhotos = obsDao.all().mapNotNull { it.photoPath }
                val data = preview.data
                db.withTransaction {
                    dao.deleteAll(); dao.insertAll(data.logs.map { it.copy(id = 0) })
                    walkDao.deleteAll(); walkDao.insertAll(data.walks.map { it.copy(id = 0) })
                    foodDao.deleteAll(); foodDao.insertAll(data.food.map { it.copy(id = 0) })
                    obsDao.deleteAll(); obsDao.insertAll(data.observations.map { it.copy(id = 0, photoPath = null) })
                }
                // The old rows are gone, so their photos would be orphans.
                oldPhotos.forEach { File(it).delete() }

                val current = profile.value ?: Profile()
                val restored = data.profile.copy(onboarded = true, geminiKey = current.geminiKey)
                store.save(restored)
                Reminders.sync(getApplication(), restored)
                BackupState.Restored
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupState.Failed(BackupFailure.WRITE)
            }
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

    /** Per-day watch data for the weekly report; null until loaded or when Health Connect is not usable. */
    private val _weekHealth = MutableStateFlow<List<DayHealth>?>(null)
    val weekHealth: StateFlow<List<DayHealth>?> = _weekHealth.asStateFlow()

    fun refreshWeekHealth() {
        viewModelScope.launch { _weekHealth.value = healthRepo.week() }
    }

    /** Workout log (newest first) for the weekly report. */
    val logs: StateFlow<List<WorkoutLog>> = dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Calories eaten per day over the last week. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val eatenKcalByDay: StateFlow<Map<Long, Int>> = flow {
        while (true) {
            emit(LocalDate.now().toEpochDay())
            delay(60_000)
        }
    }.distinctUntilChanged().flatMapLatest { today -> foodDao.observeKcalSince(today - 6) }
        .map { rows -> rows.associate { it.epochDay to it.kcal } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
