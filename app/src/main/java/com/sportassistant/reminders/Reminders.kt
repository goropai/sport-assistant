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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sportassistant.R
import com.sportassistant.data.Profile
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "reminders"
private const val KIND = "kind"

private enum class Kind(
    val workName: String,
    val title: Int,
    val body: Int,
    val daytimeOnly: Boolean,
) {
    MOVE("reminder_move", R.string.notif_move_title, R.string.notif_move_body, true),
    WATER("reminder_water", R.string.notif_water_title, R.string.notif_water_body, true),
    TEETH("reminder_teeth", R.string.notif_teeth_title, R.string.notif_teeth_body, false),
}

object Reminders {
    fun sync(context: Context, p: Profile) {
        val wm = WorkManager.getInstance(context)
        update(wm, Kind.MOVE, p.onboarded && p.remMove, 60, Duration.ofMinutes(60))
        update(wm, Kind.WATER, p.onboarded && p.remWater, 90, Duration.ofMinutes(90))
        update(wm, Kind.TEETH, p.onboarded && p.remTeeth, 12 * 60, untilNextTeethSlot())
    }

    private fun update(wm: WorkManager, kind: Kind, enabled: Boolean, everyMinutes: Long, firstDelay: Duration) {
        if (!enabled) {
            wm.cancelUniqueWork(kind.workName)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(everyMinutes, TimeUnit.MINUTES)
            .setInitialDelay(firstDelay)
            .setInputData(Data.Builder().putString(KIND, kind.name).build())
            .build()
        // KEEP: re-syncing on every settings change must not reset the user's schedule.
        wm.enqueueUniquePeriodicWork(kind.workName, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Next 08:00 or 20:00, so a 12 h period lands on both slots. */
    private fun untilNextTeethSlot(): Duration {
        val now = LocalDateTime.now()
        val next = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
            .map { now.toLocalDate().atTime(it) }
            .firstOrNull { it.isAfter(now) }
            ?: now.toLocalDate().plusDays(1).atTime(8, 0)
        return Duration.between(now, next)
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val kind = Kind.entries.firstOrNull { it.name == inputData.getString(KIND) } ?: return Result.failure()
        val hour = LocalTime.now().hour
        if (kind.daytimeOnly && hour !in 9..19) return Result.success()
        if (!Reminders.hasNotificationPermission(applicationContext)) return Result.success()

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(kind.title))
            .setContentText(applicationContext.getString(kind.body))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(kind.ordinal + 1, notification)
        }
        return Result.success()
    }
}
