package com.sportassistant.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

data class HealthSnapshot(
    val steps: Long?,
    val activeKcal: Int?,
    val distanceM: Double?,
    val avgHr: Long?,
    val minHr: Long?,
    val maxHr: Long?,
    val lastHr: Long?,
    val sleep: Duration?,
) {
    val isEmpty get() = steps == null && activeKcal == null && distanceM == null && avgHr == null && sleep == null
}

sealed interface HealthUiState {
    data object Loading : HealthUiState
    /** Health Connect is missing or must be updated; nothing the user can grant. */
    data class Unavailable(val updateRequired: Boolean) : HealthUiState
    data object NeedsPermission : HealthUiState
    data class Data(val snapshot: HealthSnapshot) : HealthUiState
    data object Error : HealthUiState
}

/**
 * Read-only access to what the watch wrote into Health Connect (via Mi Fitness).
 * We never talk to the watch directly: its protocol is closed, Health Connect is the supported route.
 */
class HealthRepository(private val context: Context) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
    )

    suspend fun load(): HealthUiState {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> Unit
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> return HealthUiState.Unavailable(updateRequired = true)
            else -> return HealthUiState.Unavailable(updateRequired = false)
        }
        val client = HealthConnectClient.getOrCreate(context)
        return try {
            if (!client.permissionController.getGrantedPermissions().containsAll(permissions)) {
                HealthUiState.NeedsPermission
            } else {
                HealthUiState.Data(read(client))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HealthUiState.Error
        }
    }

    private suspend fun read(client: HealthConnectClient): HealthSnapshot {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant()

        val today = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    DistanceRecord.DISTANCE_TOTAL,
                    HeartRateRecord.BPM_AVG,
                    HeartRateRecord.BPM_MIN,
                    HeartRateRecord.BPM_MAX,
                ),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
            ),
        )

        // Last night's sleep ends this morning, so look back from yesterday evening.
        val sleepFrom = LocalDate.now().minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val sleep = client.aggregate(
            AggregateRequest(
                metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(sleepFrom, now),
            ),
        )[SleepSessionRecord.SLEEP_DURATION_TOTAL]

        val lastHr = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofHours(6)), now),
                ascendingOrder = false,
                pageSize = 1,
            ),
        ).records.firstOrNull()?.samples?.maxByOrNull { it.time }?.beatsPerMinute

        return HealthSnapshot(
            steps = today[StepsRecord.COUNT_TOTAL],
            activeKcal = today[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toInt(),
            distanceM = today[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
            avgHr = today[HeartRateRecord.BPM_AVG],
            minHr = today[HeartRateRecord.BPM_MIN],
            maxHr = today[HeartRateRecord.BPM_MAX],
            lastHr = lastHr,
            sleep = sleep?.takeIf { !it.isZero },
        )
    }
}
