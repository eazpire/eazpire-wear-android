package com.eazpire.wear.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StepSyncHelper(private val context: Context) {
    private val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    suspend fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Installed and read permission granted — does not imply any provider writes data. */
    suspend fun canUseHealthConnect(): Boolean {
        if (!isAvailable()) return false
        val client = healthClient() ?: return false
        return client.permissionController.getGrantedPermissions().containsAll(permissions)
    }

    suspend fun hasPermissions(): Boolean = canUseHealthConnect()

    /** True when HC has returned at least one step in the last 7 days (provider pipeline active). */
    suspend fun hasRecentStepData(): Boolean {
        if (!canUseHealthConnect()) return false
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).minusDays(7).atStartOfDay(zone).toInstant()
        return readStepsBetween(start, Instant.now()) > 0
    }

    fun permissionSet() = permissions

    suspend fun readStepsToday(): Long {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        return readStepsBetween(start, Instant.now())
    }

    suspend fun readStepsBetween(start: Instant, end: Instant): Long = withContext(Dispatchers.IO) {
        if (!canUseHealthConnect()) return@withContext 0L
        val client = healthClient() ?: return@withContext 0L
        if (!end.isAfter(start)) return@withContext 0L
        runCatching {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            )
            response.records.sumOf { it.count }
        }.getOrDefault(0L)
    }

    private suspend fun healthClient(): HealthConnectClient? {
        if (!isAvailable()) return null
        return runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }
}
