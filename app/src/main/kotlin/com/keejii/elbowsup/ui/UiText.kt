package com.keejii.elbowsup.ui

import android.content.Context
import com.keejii.elbowsup.core.BlockAction
import com.keejii.elbowsup.core.BlockerStatus
import com.keejii.elbowsup.core.DraftProblem
import com.keejii.elbowsup.core.MINUTES_PER_HOUR
import com.keejii.elbowsup.core.MatcherType
import com.keejii.elbowsup.core.ScheduleProblem
import com.keejii.elbowsup.core.TimeWindow
import org.fossify.phone.R
import java.time.format.TextStyle
import java.util.Locale

fun formatMinute(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)

/** e.g. "Mon, Tue 22:00-07:00". */
fun windowText(window: TimeWindow): String {
    val days = window.days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    return "$days ${formatMinute(window.startMinute)}–${formatMinute(window.endMinute)}"
}

fun Context.matcherLabel(matcher: MatcherType): String = getString(
    when (matcher) {
        MatcherType.PREFIX -> R.string.elbowsup_matcher_prefix
        MatcherType.EXACT -> R.string.elbowsup_matcher_exact
        MatcherType.NAME_WILDCARD -> R.string.elbowsup_matcher_name
        MatcherType.EMPTY_NAME -> R.string.elbowsup_matcher_empty_name
        MatcherType.NO_NUMBER -> R.string.elbowsup_matcher_no_number
    },
)

fun Context.actionLabel(action: BlockAction): String = getString(
    when (action) {
        BlockAction.REJECT_QUIET -> R.string.elbowsup_action_reject_quiet
        BlockAction.REJECT -> R.string.elbowsup_action_reject
        BlockAction.SILENCE -> R.string.elbowsup_action_silence
        BlockAction.ANSWER_HANGUP -> R.string.elbowsup_action_answer_hangup
    },
)

fun Context.problemText(problem: DraftProblem): String = getString(
    when (problem) {
        DraftProblem.PATTERN_REQUIRED -> R.string.elbowsup_error_pattern_required
        DraftProblem.NOT_A_NUMBER -> R.string.elbowsup_error_not_a_number
        DraftProblem.NO_DAYS -> R.string.elbowsup_error_no_days
    },
)

fun Context.problemText(problem: ScheduleProblem): String = getString(
    when (problem) {
        ScheduleProblem.NAME_REQUIRED -> R.string.elbowsup_error_name_required
        ScheduleProblem.NO_DAYS -> R.string.elbowsup_error_no_days
    },
)

/** The one word shown as the status headline. */
fun Context.statusHeadline(status: BlockerStatus): String = getString(
    when (status) {
        BlockerStatus.ACTIVE -> R.string.elbowsup_status_active
        BlockerStatus.UNSUPPORTED -> R.string.elbowsup_status_unavailable
        BlockerStatus.NEEDS_SETUP,
        BlockerStatus.SCREENING_MISSING,
        BlockerStatus.CONTACTS_PERMISSION_MISSING -> R.string.elbowsup_status_needs_setup
        BlockerStatus.PAUSED_TIMED,
        BlockerStatus.PAUSED_UNTIL_RESUME,
        BlockerStatus.PAUSED_NEXT_CALL,
        BlockerStatus.PAUSED_SCHEDULE -> R.string.elbowsup_status_paused
    },
)

fun Context.statusDetail(status: BlockerStatus, untilLabel: String): String = when (status) {
    BlockerStatus.ACTIVE -> getString(R.string.elbowsup_detail_active)
    BlockerStatus.UNSUPPORTED -> getString(R.string.elbowsup_detail_unsupported)
    BlockerStatus.NEEDS_SETUP -> getString(R.string.elbowsup_detail_needs_setup)
    BlockerStatus.SCREENING_MISSING -> getString(R.string.elbowsup_detail_screening_missing)
    BlockerStatus.CONTACTS_PERMISSION_MISSING -> getString(R.string.elbowsup_detail_contacts_missing)
    BlockerStatus.PAUSED_TIMED -> getString(R.string.elbowsup_detail_paused_until, untilLabel)
    BlockerStatus.PAUSED_UNTIL_RESUME -> getString(R.string.elbowsup_detail_paused_until_resume)
    BlockerStatus.PAUSED_NEXT_CALL -> getString(R.string.elbowsup_detail_paused_next_call)
    BlockerStatus.PAUSED_SCHEDULE -> getString(R.string.elbowsup_detail_paused_schedule)
}
