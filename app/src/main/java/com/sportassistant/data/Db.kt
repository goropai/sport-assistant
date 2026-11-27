package com.sportassistant.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
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

@Dao
interface FoodDao {
    @Insert
    suspend fun insertAll(entries: List<FoodEntry>)

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM food_entry WHERE epochDay = :day ORDER BY loggedAt DESC")
    fun observeDay(day: Long): Flow<List<FoodEntry>>
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

@Database(entities = [WorkoutLog::class, Walk::class, FoodEntry::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): WorkoutLogDao
    abstract fun walkDao(): WalkDao
    abstract fun foodDao(): FoodDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    )
}
