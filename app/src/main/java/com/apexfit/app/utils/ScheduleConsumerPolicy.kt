package com.apexfit.app.utils

import java.time.LocalDate

/** The daily nutrition policy uses actual completed activity or the resolved plan.
 * A reservation alone is not exercise evidence and does not change long-run TDEE.
 */
object ScheduleConsumerPolicy {
    fun isTrainingDay(date: LocalDate, choice: TodayWorkoutChoice?, completedWorkouts: Int): Boolean =
        completedWorkouts > 0 || (choice?.date == date && choice.session != null)

    fun readinessSession(date: LocalDate, choice: TodayWorkoutChoice?, completedWorkouts: Int): String? =
        choice?.session?.label?.takeIf {
            choice.date == date && completedWorkouts <= 0 &&
                choice.kind in setOf(TodayWorkoutKind.REGULAR, TodayWorkoutKind.RESERVED)
        }

    /** Day-specific notifications must never roll over to the next local date. */
    fun sameDayNotification(scheduledDate: String?, actualDate: LocalDate): Boolean =
        scheduledDate == actualDate.toString()
}
