package com.apexfit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.data.TrainingSession
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private sealed interface RescheduleCandidates {
    data object Loading : RescheduleCandidates
    data object Failed : RescheduleCandidates
    data class Ready(val sessions: List<TrainingSession>) : RescheduleCandidates
}

private sealed interface CalendarLoad {
    data object Loading : CalendarLoad
    data object Failed : CalendarLoad
    data class Ready(val month: TrainingCalendarMonth) : CalendarLoad
}

@Composable
internal fun TrainingCalendarScreen(progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    val date by remember { DateTimeUtils.todayDateFlow() }
        .collectAsStateWithLifecycle(initialValue = DateTimeUtils.todayDateString())
    val today = LocalDate.parse(date)
    var chosenMonth by rememberSaveable { mutableStateOf<String?>(null) }
    val month = chosenMonth?.let(YearMonth::parse) ?: YearMonth.from(today)
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPeriodReports by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var decisionBusy by remember { mutableStateOf(false) }
    var decisionError by remember { mutableStateOf<String?>(null) }
    val loaded by key(month, today, retry) {
        produceState<CalendarLoad>(CalendarLoad.Loading) {
            try {
                progressViewModel.observeCalendar(month, today).collect { value = CalendarLoad.Ready(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = CalendarLoad.Failed }
        }
    }
    val data = (loaded as? CalendarLoad.Ready)?.month
    val day = data?.days?.firstOrNull { it.date.toString() == selectedDate }
    val selectedSession = (day?.sessions.orEmpty() + listOfNotNull(day?.rescheduledWorkout))
        .firstOrNull { it.id == sessionId }
    var linkTarget by remember { mutableStateOf<TrainingCalendarDay?>(null) }
    var candidateRetry by remember { mutableIntStateOf(0) }
    var selectedCandidate by remember { mutableStateOf<String?>(null) }
    val target = linkTarget
    val candidates by produceState<RescheduleCandidates>(RescheduleCandidates.Loading, target, candidateRetry) {
        value = RescheduleCandidates.Loading
        if (target != null) {
            try {
                value = RescheduleCandidates.Ready(progressViewModel.reschedulingCandidates(
                    target.date, requireNotNull(target.recordedOccurrenceId)))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = RescheduleCandidates.Failed }
        }
    }
    BackHandler(enabled = sessionId != null || showPeriodReports) {
        if (showPeriodReports) showPeriodReports = false else sessionId = null
    }
    if (showPeriodReports) {
        TrainingPeriodReportScreen(progressViewModel, today, onBack = { showPeriodReports = false })
        return
    }
    if (selectedSession != null) {
        SavedWorkoutReportScreen(selectedSession.id, progressViewModel, onBack = { sessionId = null })
        return
    }
    TrainingCalendarContent(month, today, data, selectedDate?.let(LocalDate::parse),
        loadError = loaded is CalendarLoad.Failed, onBack = onBack, onRetry = { retry++ },
        onMonthChange = { chosenMonth = it.toString(); selectedDate = null; sessionId = null; decisionError = null },
        onDaySelected = { selectedDate = it.toString(); sessionId = null; decisionError = null },
        onSessionSelected = { sessionId = it.id },
        onOpenReports = { showPeriodReports = true },
        decisionBusy = decisionBusy, decisionError = decisionError,
        onLinkWorkout = { selectedCandidate = null; decisionError = null; linkTarget = it },
        onUnlinkWorkout = { targetDay ->
            val id = targetDay.recordedOccurrenceId
            if (!decisionBusy && id != null) {
                decisionBusy = true
                decisionError = null
                scope.launch {
                    try { progressViewModel.unlinkRescheduledWorkout(targetDay.date, id) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { decisionError = "Could not remove association. Try again." }
                    finally { decisionBusy = false }
                }
            }
        },
        onDecision = { target, decision ->
            val id = target.recordedOccurrenceId
            if (!decisionBusy && id != null) {
                decisionBusy = true
                decisionError = null
                scope.launch {
                    try { progressViewModel.setOccurrenceDecision(target.date, id, decision) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { decisionError = "Could not update schedule outcome. Try again." }
                    finally { decisionBusy = false }
                }
            }
        })
    if (target != null) {
        val options = (candidates as? RescheduleCandidates.Ready)?.sessions.orEmpty()
        val chosen = options.firstOrNull { it.id == selectedCandidate }
        AlertDialog(
            onDismissRequest = { if (!decisionBusy) linkTarget = null },
            title = { Text("Link rescheduled workout") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Credit ${target.plannedLabel ?: "this workout"} scheduled for ${DateTimeUtils.formatDateForDisplay(target.date)}. Your actual training date will not change.")
                    when (val state = candidates) {
                        RescheduleCandidates.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
                        RescheduleCandidates.Failed -> {
                            Text("Could not load eligible workouts.")
                            TextButton(onClick = { candidateRetry++ }) { Text("Retry") }
                        }
                        is RescheduleCandidates.Ready -> if (state.sessions.isEmpty()) {
                            Text("No eligible unlinked workouts for this exact plan slot on another date.")
                        } else LazyColumn(Modifier.heightIn(max = 250.dp)) {
                            items(state.sessions, key = { it.id }) { workout ->
                                Row(Modifier.fillMaxWidth().clickable(enabled = !decisionBusy,
                                    role = Role.RadioButton) { selectedCandidate = workout.id },
                                    verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selectedCandidate == workout.id, onClick = null)
                                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                        Text(workout.sessionType)
                                        Text("${DateTimeUtils.formatDateForDisplay(workout.date)} · ${if (workout.completed) "Completed" else "Partial"}",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    decisionError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = !decisionBusy && chosen != null, onClick = {
                    val workout = chosen ?: return@TextButton
                    decisionBusy = true
                    decisionError = null
                    scope.launch {
                        try {
                            progressViewModel.linkRescheduledWorkout(target.date,
                                requireNotNull(target.recordedOccurrenceId), workout.id)
                            linkTarget = null
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            decisionError = "Workout could not be linked. Check the available workouts and retry."
                            selectedCandidate = null
                            candidateRetry++
                        } finally { decisionBusy = false }
                    }
                }) { Text(if (decisionBusy) "Saving…" else "Link workout") }
            },
            dismissButton = { TextButton(enabled = !decisionBusy, onClick = { linkTarget = null; decisionError = null }) { Text("Cancel") } }
        )
    }
}

private fun logLabel(day: TrainingCalendarDay): String = when (day.logKind) {
    CalendarLogKind.COMPLETED -> "Completed"
    CalendarLogKind.PARTIAL -> "Partial"
    CalendarLogKind.NONE -> "No saved workout"
}
private fun occurrenceLabel(day: TrainingCalendarDay): String? = when (day.occurrenceStatus) {
    RecordedOccurrenceStatus.NOT_APPLICABLE -> null
    RecordedOccurrenceStatus.COMPLETED -> "Scheduled workout completed"
    RecordedOccurrenceStatus.PARTIAL -> "Scheduled workout partially completed"
    RecordedOccurrenceStatus.MISSED -> "Marked missed"
    RecordedOccurrenceStatus.EXCUSED -> "Marked excused"
    RecordedOccurrenceStatus.UNRESOLVED -> "Scheduled workout · outcome unconfirmed"
}
private fun scheduleLabel(day: TrainingCalendarDay): String = when (day.scheduleKind) {
    CalendarScheduleKind.TRAINING -> if (day.scheduleSource == CalendarScheduleSource.RECORDED)
        "Recorded schedule: ${day.plannedLabel}" else "Projected: ${day.plannedLabel}"
    CalendarScheduleKind.REST -> if (day.scheduleSource == CalendarScheduleSource.RECORDED)
        "Recorded rest day" else "Projected rest day"
    CalendarScheduleKind.UNKNOWN -> "Schedule unknown"
}

@Composable
internal fun TrainingCalendarContent(
    month: YearMonth, today: LocalDate, data: TrainingCalendarMonth?, selectedDate: LocalDate?,
    loadError: Boolean = false, onBack: () -> Unit, onRetry: () -> Unit = {},
    onMonthChange: (YearMonth) -> Unit = {}, onDaySelected: (LocalDate) -> Unit = {},
    onSessionSelected: (TrainingSession) -> Unit = {},
    decisionBusy: Boolean = false, decisionError: String? = null,
    onDecision: (TrainingCalendarDay, OccurrenceDecision?) -> Unit = { _, _ -> },
    onLinkWorkout: (TrainingCalendarDay) -> Unit = {},
    onUnlinkWorkout: (TrainingCalendarDay) -> Unit = {},
    onOpenReports: () -> Unit = {}
) {
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val day = data?.days?.firstOrNull { it.date == selectedDate }
    val adherence = data?.let { ScheduleAdherence.summarize(it.days, today) }
    LazyColumn(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back to Progress") }
            Text("Training calendar", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onOpenReports) { Text("Weekly and monthly reports") }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Text("Previous") }
                Text(month.format(monthFormat), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Text("Next") }
            }
            TextButton(onClick = { onMonthChange(YearMonth.from(today)); onDaySelected(today) }) { Text("Today") }
        }
        if (loadError) item {
            Text("Calendar couldn't load.", color = ApexColors.TextSecondary)
            TextButton(onClick = onRetry) { Text("Try again") }
        } else if (data == null) item { CircularProgressIndicator(Modifier.size(24.dp)) }
        else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${data.completedDays} completed days", style = MaterialTheme.typography.titleMedium)
                        Text("${data.completedSessions} completed workouts", color = ApexColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${data.partialOnlyDays} partial-only days", style = MaterialTheme.typography.titleMedium)
                        Text("${data.partialSessions} partial workouts", color = ApexColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (adherence != null && adherence.elapsedDays > 0) item {
                val s = adherence
                fun percent(value: Double): String = java.text.NumberFormat.getPercentInstance().format(value)
                Text("Scheduled completion", style = MaterialTheme.typography.titleMedium)
                val rate = s.completionRate
                val low = s.completionLowerBound
                val high = s.completionUpperBound
                val description = when {
                    rate != null -> "${percent(rate)} completed (${s.completed}/${s.eligible})"
                    low != null && high != null -> "${percent(low)}–${percent(high)} possible completion"
                    else -> "No eligible recorded training days"
                }
                Text(description, style = MaterialTheme.typography.titleLarge)
                Text("${s.resolvedEligible}/${s.eligible} eligible outcomes resolved · ${s.excused} excused",
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                Text("Schedule recorded for ${s.knownScheduleDays}/${s.elapsedDays} elapsed days. Partial workouts are not full completions.",
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                if (s.unresolved > 0) Text("Range reflects all possible outcomes of unconfirmed workouts, not a confidence interval.",
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
            }
            if (data.recordedOccurrences > 0) item {
                Text("Recorded schedule outcomes", style = MaterialTheme.typography.titleMedium)
                Text("${data.recordedCompletedOccurrences} completed · ${data.recordedPartialOccurrences} partial · ${data.unresolvedOccurrences} unconfirmed",
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                Text("${data.missedOccurrences} missed · ${data.excusedOccurrences} excused",
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                Text("Based on ${data.recordedOccurrences} recorded training days before today. Unconfirmed is not a missed workout.",
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = ApexColors.TextSecondary)
                            }
                        }
                    }
                    val offset = month.atDay(1).dayOfWeek.value - 1
                    val slots = List<TrainingCalendarDay?>(offset) { null } + data.days
                    val padded = slots + List<TrainingCalendarDay?>((7 - slots.size % 7) % 7) { null }
                    padded.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { d ->
                                if (d == null) Spacer(Modifier.weight(1f).height(52.dp))
                                else CalendarDayCell(d, today, d.date == selectedDate,
                                    Modifier.weight(1f), onDaySelected)
                            }
                        }
                    }
                }
                Text("C saved completed · P saved partial · T scheduled · R rest · — unknown",
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                Text("Past schedules use recorded revisions where available. Only exact or explicitly associated workouts establish scheduled completion. No log is not proof of a missed workout. Today and future dates show the live plan.",
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
            }
            if (day == null) item { Text("Select a day for details.", color = ApexColors.TextSecondary) }
            else {
                item {
                    HorizontalDivider(color = ApexColors.BorderSubtle)
                    Text(day.date.format(dateFormat), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp))
                    Text(logLabel(day), color = ApexColors.TextSecondary)
                    Text(scheduleLabel(day), color = ApexColors.TextSecondary)
                    day.planName?.let {
                        Text(if (day.scheduleSource == CalendarScheduleSource.RECORDED) "Recorded plan: $it"
                            else "Current plan: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    occurrenceLabel(day)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                    }
                    if (!day.hasManualAssociation && day.occurrenceStatus in setOf(RecordedOccurrenceStatus.UNRESOLVED,
                            RecordedOccurrenceStatus.MISSED, RecordedOccurrenceStatus.EXCUSED)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !decisionBusy && day.explicitDecision != OccurrenceDecision.MISSED,
                                onClick = { onDecision(day, OccurrenceDecision.MISSED) }) { Text("Mark missed") }
                            TextButton(enabled = !decisionBusy && day.explicitDecision != OccurrenceDecision.EXCUSED,
                                onClick = { onDecision(day, OccurrenceDecision.EXCUSED) }) { Text("Excuse") }
                        }
                    }
                    if (day.hasManualAssociation) {
                        val workout = day.rescheduledWorkout
                        if (workout != null) {
                            Text("Linked workout · ${DateTimeUtils.formatDateForDisplay(workout.date)}",
                                style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onSessionSelected(workout) }) { Text("View linked workout") }
                        } else Text("Saved association no longer verifies. Remove it to choose another workout.",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = !decisionBusy, onClick = { onUnlinkWorkout(day) }) {
                            Text("Remove workout link")
                        }
                    } else if (day.occurrenceStatus in setOf(RecordedOccurrenceStatus.UNRESOLVED,
                            RecordedOccurrenceStatus.MISSED, RecordedOccurrenceStatus.EXCUSED)) {
                        TextButton(enabled = !decisionBusy, onClick = { onLinkWorkout(day) }) {
                            Text("Link workout from another day")
                        }
                    }
                    if (day.explicitDecision != null) {
                        TextButton(enabled = !decisionBusy, onClick = { onDecision(day, null) }) {
                            Text("Clear manual decision")
                        }
                    }
                    decisionError?.let { Text(it, color = ApexColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall) }
                }
                items(day.sessions, key = { it.id }) { session ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                        onSessionSelected(session)
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.sessionType.ifBlank { "Workout" }, style = MaterialTheme.typography.titleMedium)
                            Text((if (session.completed) "Completed" else "Partial") +
                                " · ${session.durationMinutes.coerceAtLeast(0)} min", color = ApexColors.TextSecondary)
                            if (day.recordedOccurrenceId != null) {
                                val linked = session.scheduleOccurrenceId == day.recordedOccurrenceId &&
                                    session.planSessionId == day.plannedSessionId &&
                                    session.startedAt != null && session.completedAt != null &&
                                    session.completedAt >= session.startedAt &&
                                    ((session.completed && session.status == "completed") ||
                                        (!session.completed && session.status == "partial"))
                                Text(if (linked) "Linked to recorded schedule" else "Not verified against recorded schedule",
                                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                            }
                        }
                        Text("›", color = ApexColors.TextSecondary)
                    }
                    HorizontalDivider(color = ApexColors.BorderSubtle)
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(day: TrainingCalendarDay, today: LocalDate, selected: Boolean,
                            modifier: Modifier, onDaySelected: (LocalDate) -> Unit) {
    val marker = when (day.logKind) {
        CalendarLogKind.COMPLETED -> if (day.partialCount > 0) "C+" else "C"
        CalendarLogKind.PARTIAL -> "P"
        CalendarLogKind.NONE -> when (day.scheduleKind) {
            CalendarScheduleKind.TRAINING -> "T"
            CalendarScheduleKind.REST -> "R"
            CalendarScheduleKind.UNKNOWN -> "—"
        }
    }
    val color = when {
        day.logKind == CalendarLogKind.COMPLETED -> ApexColors.Primary
        else -> ApexColors.TextSecondary
    }
    Column(modifier.height(52.dp)
        .background(if (selected) ApexColors.SurfaceSunken else ApexColors.Canvas)
        .clickable(role = Role.Button) { onDaySelected(day.date) }
        .semantics { contentDescription = listOfNotNull(day.date.toString(), logLabel(day), scheduleLabel(day),
            occurrenceLabel(day), if (day.date == today) "today" else null).joinToString(", ") },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(day.date.dayOfMonth.toString(), color = if (day.date == today) ApexColors.Primary else ApexColors.TextPrimary)
        Text(marker, color = color, style = MaterialTheme.typography.labelSmall)
    }
}
