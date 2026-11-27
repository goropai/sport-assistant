package com.sportassistant.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workout_log")
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val exercises: Int,
    val seconds: Int,
    val points: Int,
)

@Dao
interface WorkoutLogDao {
    @Insert
    suspend fun insert(log: WorkoutLog)

    @Query("SELECT * FROM workout_log ORDER BY id DESC")
    fun observeAll(): Flow<List<WorkoutLog>>

    @Query("SELECT * FROM workout_log ORDER BY id")
    suspend fun all(): List<WorkoutLog>

    @Insert
    suspend fun insertAll(items: List<WorkoutLog>)

    @Query("DELETE FROM workout_log")
    suspend fun deleteAll()
}

@Entity(tableName = "walk")
data class Walk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val durationSec: Long,
    val distanceM: Double,
    val kcal: Int,
    /** "lat,lon;lat,lon;..." */
    val path: String,
)

@Dao
interface WalkDao {
    @Insert
    suspend fun insert(walk: Walk)

    @Query("SELECT * FROM walk ORDER BY id DESC LIMIT 30")
    fun observeRecent(): Flow<List<Walk>>

    @Query("SELECT * FROM walk ORDER BY id")
    suspend fun all(): List<Walk>

    @Insert
    suspend fun insertAll(items: List<Walk>)

    @Query("DELETE FROM walk")
    suspend fun deleteAll()
}

@Entity(tableName = "food_entry")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val loggedAt: Long,
    val name: String,
    val grams: Int,
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
)

data class DayKcal(val epochDay: Long, val kcal: Int)

@Dao
interface FoodDao {
    @Insert
    suspend fun insertAll(entries: List<FoodEntry>)

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Update
    suspend fun update(entry: FoodEntry)

    /** Most recent entries, the raw material for the "frequent foods" suggestions. */
    @Query("SELECT * FROM food_entry ORDER BY loggedAt DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entry ORDER BY id")
    suspend fun all(): List<FoodEntry>

    @Query("DELETE FROM food_entry")
    suspend fun deleteAll()

    @Query("SELECT * FROM food_entry WHERE epochDay = :day ORDER BY loggedAt DESC")
    fun observeDay(day: Long): Flow<List<FoodEntry>>

    @Query("SELECT epochDay, SUM(kcal) AS kcal FROM food_entry WHERE epochDay >= :fromDay GROUP BY epochDay")
    fun observeKcalSince(fromDay: Long): Flow<List<DayKcal>>
}

/** A saved posture/skin photo analysis. [photoPath] is an app-private file, null if the user chose not to keep it. */
@Entity(tableName = "observation")
data class Observation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val type: String,
    val summary: String,
    /** Observations, one per line. */
    val details: String,
    /** Suggestions, one per line. */
    val suggestions: String,
    val seeDoctor: Boolean,
    val doctorReason: String,
    val photoPath: String?,
)

@Dao
interface ObservationDao {
    @Insert
    suspend fun insert(o: Observation)

    @Query("SELECT * FROM observation WHERE id = :id")
    suspend fun get(id: Long): Observation?

    @Query("DELETE FROM observation WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM observation ORDER BY id DESC")
    fun observeAll(): Flow<List<Observation>>

    @Query("SELECT * FROM observation ORDER BY id")
    suspend fun all(): List<Observation>

    @Insert
    suspend fun insertAll(items: List<Observation>)

    @Query("DELETE FROM observation")
    suspend fun deleteAll()
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `observation` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `type` TEXT NOT NULL, `summary` TEXT NOT NULL, `details` TEXT NOT NULL, " +
                "`suggestions` TEXT NOT NULL, `seeDoctor` INTEGER NOT NULL, `doctorReason` TEXT NOT NULL, `photoPath` TEXT)",
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `loggedAt` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`grams` INTEGER NOT NULL, `kcal` INTEGER NOT NULL, `proteinG` INTEGER NOT NULL, " +
                "`fatG` INTEGER NOT NULL, `carbsG` INTEGER NOT NULL)",
        )
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `walk` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `durationSec` INTEGER NOT NULL, `distanceM` REAL NOT NULL, " +
                "`kcal` INTEGER NOT NULL, `path` TEXT NOT NULL)",
        )
    }
}

@Database(entities = [WorkoutLog::class, Walk::class, FoodEntry::class, Observation::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun observationDao(): ObservationDao
    abstract fun logDao(): WorkoutLogDao
    abstract fun walkDao(): WalkDao
    abstract fun foodDao(): FoodDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}

data class Stats(
    val workouts: Int,
    val points: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    /** Oldest first: today-6 .. today. */
    val last7: List<Boolean>,
    /** Active days among the 6 days before today. Excludes today so today's plan stays stable all day. */
    val activeDaysPrev6: Int,
    val hadWorkoutBeforeToday: Boolean,
)

fun computeStats(logs: List<WorkoutLog>, today: Long): Stats {
    val days = logs.map { it.epochDay }.toSortedSet()

    var best = 0
    var run = 0
    var prev: Long? = null
    for (d in days) {
        run = if (prev != null && d == prev + 1) run + 1 else 1
        best = maxOf(best, run)
        prev = d
    }

    // The streak stays alive until the end of the day after the last workout.
    var cursor = if (today in days) today else today - 1
    var current = 0
    while (cursor in days) {
        current++
        cursor--
    }

    return Stats(
        workouts = logs.size,
        points = logs.sumOf { it.points },
        currentStreak = current,
        bestStreak = best,
        last7 = (today - 6..today).map { it in days },
        activeDaysPrev6 = (today - 6 until today).count { it in days },
        hadWorkoutBeforeToday = days.any { it < today },
    )
}
