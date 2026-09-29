package com.stepadder.app

import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneId

object StepWriter {

    /** The single permission this app ever asks for. */
    val PERMISSIONS: Set<String> = setOf(HealthPermission.getWritePermission(StepsRecord::class))

    /**
     * Writes [total] steps ending at [end] as automatically recorded phone data.
     *
     * Each record carries RECORDING_METHOD_AUTOMATICALLY_RECORDED and this phone
     * (type PHONE, manufacturer/model from the OS) as the recording device — the
     * same shape a pedometer app writes — rather than RECORDING_METHOD_MANUAL_ENTRY.
     *
     * Returns the Health Connect record IDs so the write can be undone.
     */
    suspend fun write(client: HealthConnectClient, total: Long, end: Instant): List<String> {
        val device = Device(
            type = Device.TYPE_PHONE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
        )
        val rules = ZoneId.systemDefault().rules
        val records = StepPlanner.plan(total, end).map { chunk ->
            StepsRecord(
                startTime = chunk.start,
                startZoneOffset = rules.getOffset(chunk.start),
                endTime = chunk.end,
                endZoneOffset = rules.getOffset(chunk.end),
                count = chunk.count,
                metadata = Metadata.autoRecorded(device = device),
            )
        }
        // Health Connect accepts large batches, but stay well under its per-call limits.
        return records.chunked(500).flatMap { batch ->
            client.insertRecords(batch).recordIdsList
        }
    }

    /** Deletes records this app wrote (the write permission covers deleting your own records). */
    suspend fun undo(client: HealthConnectClient, ids: List<String>) {
        ids.chunked(500).forEach { batch ->
            client.deleteRecords(StepsRecord::class, recordIdsList = batch, clientRecordIdsList = emptyList())
        }
    }
}
