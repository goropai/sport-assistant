package com.sportassistant.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sportassistant.R
import com.sportassistant.data.Profile
import com.sportassistant.data.ProfileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

private const val CHANNEL_ID = "reminders"
private const val KIND = "kind"
private const val SCHEDULED_AT = "scheduled_at"

/** Water is offset from movement so the two reminders do not land on the same minute. */
private const val WATER_OFFSET_MIN = 30

private enum class Kind(val workName: String, val title: Int, val body: Int) {
    MOVE("reminder_move", R.string.notif_move_title, R.string.notif_move_body),
    WATER("reminder_water", R.string.notif_water_title, R.string.notif_water_body),
    TEETH("reminder_teeth", R.string.notif_teeth_title, R.string.notif_teeth_body),
}

/** The next time this reminder is due, or null when it is switched off. */
private fun Kind.nextFor(p: Profile, now: LocalDateTime): LocalDateTime? {
    if (!p.onboarded) return null
    return when (this) {
        Kind.MOVE -> if (p.remMove) {
            Schedule.nextInterval(now, p.dayStartMin, p.dayEndMin, p.moveEveryMin, p.weekdaysOnly)
        } else null
        Kind.WATER -> if (p.remWater) {
            Schedule.nextInterval(now, p.dayStartMin, p.dayEndMin, p.waterEveryMin, p.weekdaysOnly, WATER_OFFSET_MIN)
        } else null
        Kind.TEETH -> if (p.remTeeth) Schedule.nextFixed(now, listOf(p.teethMorningMin, p.teethEveningMin)) else null
    }
}

/**
 * Each reminder is a one-off job that schedules its successor, so any change of settings simply
 * reschedules, and the next time is always computed from a pure, tested rule ([Schedule]).
 */
object Reminders {
    /** Applies the current settings. Safe to call often: the next time is deterministic. */
    fun sync(context: Context, p: Profile) {
        val wm = WorkManager.getInstance(context)
        val now = LocalDateTime.now()
        for (kind in Kind.entries) {
            val next = kind.nextFor(p, now)
            if (next == null) wm.cancelUniqueWork(kind.workName) else enqueue(wm, kind, next, now)
        }
    }

    private fun enqueue(wm: WorkManager, kind: Kind, at: LocalDateTime, now: LocalDateTime) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, at).coerceAtLeast(Duration.ZERO))
            .setInputData(
                Data.Builder()
                    .putString(KIND, kind.name)
                    .putLong(SCHEDULED_AT, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    .build(),
            )
            .build()
        // REPLACE also supersedes the periodic jobs older app versions registered under the same names.
        wm.enqueueUniqueWork(kind.workName, ExistingWorkPolicy.REPLACE, request)
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    internal fun scheduleNext(context: Context, kind: String, p: Profile) {
        val k = Kind.entries.firstOrNull { it.name == kind } ?: return
        val now = LocalDateTime.now()
        k.nextFor(p, now)?.let { enqueue(WorkManager.getInstance(context), k, it, now) }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val kind = Kind.entries.firstOrNull { it.name == inputData.getString(KIND) } ?: return Result.failure()

        // Schedule the successor first, so a failure while notifying can never end the chain.
        val profile = runBlocking { ProfileStore(applicationContext).profile.first() }
        Reminders.scheduleNext(applicationContext, kind.name, profile)

        val scheduledAt = inputData.getLong(SCHEDULED_AT, System.currentTimeMillis())
        if (Schedule.isStale(scheduledAt, System.currentTimeMillis())) return Result.success() // device slept through it
        if (kind.nextFor(profile, LocalDateTime.now().minusMinutes(1)) == null) return Result.success() // switched off meanwhile
        if (!Reminders.hasNotificationPermission(applicationContext)) return Result.success()

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(kind.title))
            .setContentText(applicationContext.getString(kind.body))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(kind.ordinal + 1, notification) }
        return Result.success()
    }
}
