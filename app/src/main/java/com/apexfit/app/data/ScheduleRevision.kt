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

/** Current explicit user decision; independent of mutable plan/session rows. Clearing removes the decision. */
@Entity(tableName = "schedule_dispositions", indices = [Index(value = ["scheduledDate"])])
data class ScheduleDisposition(
    @PrimaryKey val occurrenceId: String,
    val scheduledDate: String,
    val decision: String,
    val recordedAt: Long
)

/** Explicit association, not a change to the original schedule or actual workout date.
 * One saved session can satisfy at most one manually linked occurrence.
 */
@Entity(tableName = "schedule_workout_associations",
    indices = [Index(value = ["sessionId"], unique = true), Index(value = ["scheduledDate"])],
    foreignKeys = [ForeignKey(entity = TrainingSession::class, parentColumns = ["id"],
        childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class ScheduleWorkoutAssociation(
    @PrimaryKey val occurrenceId: String,
    val scheduledDate: String,
    val sessionId: String,
    val recordedAt: Long
)

data class RescheduledWorkoutSnapshot(
    @Embedded val association: ScheduleWorkoutAssociation,
    @Relation(parentColumn = "sessionId", entityColumn = "id") val session: TrainingSession?
)
