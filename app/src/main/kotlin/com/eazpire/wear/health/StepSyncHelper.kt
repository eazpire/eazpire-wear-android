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

    suspend fun hasPermissions(): Boolean {
        val client = healthClient() ?: return false
        return client.permissionController.getGrantedPermissions().containsAll(permissions)
    }

    fun permissionSet() = permissions

    suspend fun readStepsToday(): Long {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        return readStepsBetween(start, Instant.now())
    }

    suspend fun readStepsBetween(start: Instant, end: Instant): Long = withContext(Dispatchers.IO) {
        val client = healthClient() ?: return@withContext 0L
        if (!end.isAfter(start)) return@withContext 0L
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        )
        response.records.sumOf { it.count }
    }

    private suspend fun healthClient(): HealthConnectClient? {
        if (!isAvailable()) return null
        return runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }
}
