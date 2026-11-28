package com.apexfit.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Append-only plan state. Null planId means the schedule cannot be established. */
@Entity(tableName = "schedule_revisions", indices = [Index(value = ["effectiveFrom"])])
data class ScheduleRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val effectiveFrom: String,
    val recordedAt: Long,
    val planId: Long?,
    val planName: String?,
    val cycleWeeks: Int?,
    val anchorMonday: String?
)

/** Snapshot values deliberately do not reference the mutable plan/session tables. */
@Entity(tableName = "schedule_revision_slots",
    primaryKeys = ["revisionId", "slotId"],
    foreignKeys = [ForeignKey(entity = ScheduleRevision::class, parentColumns = ["id"],
        childColumns = ["revisionId"], onDelete = ForeignKey.CASCADE)])
data class ScheduleRevisionSlot(
    val revisionId: Long,
    val slotId: Long,
    val cycleWeek: Int,
    val weekday: Int,
    val label: String,
    val training: Boolean
)

data class ScheduleRevisionSnapshot(
    @Embedded val revision: ScheduleRevision,
    @Relation(parentColumn = "id", entityColumn = "revisionId") val slots: List<ScheduleRevisionSlot>
)
