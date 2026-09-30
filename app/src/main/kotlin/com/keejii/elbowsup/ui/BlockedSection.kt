package com.keejii.elbowsup.ui

import android.view.LayoutInflater
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.MatcherType
import com.keejii.elbowsup.core.Rule
import com.keejii.elbowsup.core.RuleKind
import com.keejii.elbowsup.core.addRule
import com.keejii.elbowsup.storage.BlockedEvent
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.formatPhoneNumber
import org.fossify.commons.extensions.toast
import org.fossify.commons.models.RadioItem
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ActivityElbowsupBlockerBinding
import org.fossify.phone.databinding.ItemElbowsupBlockedBinding

private const val SHOWN_EVENTS = 10
private const val CHOICE_ALLOW = 1
private const val CHOICE_REMOVE = 2

/** The most recent blocked calls, with a way to allow a number that should not have been blocked. */
class BlockedSection(
    private val activity: SimpleActivity,
    private val binding: ActivityElbowsupBlockerBinding,
    private val runtime: BlockerRuntime,
    private val onChanged: () -> Unit,
) {
    init {
        binding.elbowsupClearBlocked.setOnClickListener {
            runtime.events.clear()
            onChanged()
        }
    }

    fun render() {
        binding.elbowsupBlockedList.removeAllViews()
        val events = runtime.events.all().take(SHOWN_EVENTS)
        binding.elbowsupBlockedEmpty.beVisibleIf(events.isEmpty())
        binding.elbowsupClearBlocked.beVisibleIf(events.isNotEmpty())
        val inflater = LayoutInflater.from(activity)
        events.forEach { event ->
            val row = ItemElbowsupBlockedBinding.inflate(inflater, binding.elbowsupBlockedList, false)
            row.elbowsupBlockedTitle.text = title(event)
            val time = event.timeEpochMillis.formatDateOrTime(
                activity,
                hideTimeOnOtherDays = false,
                showCurrentYear = false,
                hideTodaysDate = false,
            )
            row.elbowsupBlockedSubtitle.text = activity.getString(R.string.elbowsup_joined, time, event.ruleSummary)
            row.elbowsupBlockedHolder.setOnClickListener { choose(event) }
            binding.elbowsupBlockedList.addView(row.root)
        }
    }

    private fun title(event: BlockedEvent): String {
        val number = event.number?.formatPhoneNumber() ?: activity.getString(R.string.elbowsup_blocked_hidden)
        return event.displayName?.let { activity.getString(R.string.elbowsup_joined, it, number) } ?: number
    }

    private fun choose(event: BlockedEvent) {
        val number = event.number
        val items = arrayListOf<RadioItem>()
        if (number != null) items += RadioItem(CHOICE_ALLOW, activity.getString(R.string.elbowsup_blocked_allow))
        items += RadioItem(CHOICE_REMOVE, activity.getString(R.string.elbowsup_blocked_remove))
        RadioGroupDialog(activity, items, titleId = R.string.elbowsup_blocked_dialog_title) { chosen ->
            when (chosen as Int) {
                CHOICE_ALLOW -> if (number != null) allow(number)
                CHOICE_REMOVE -> {
                    runtime.events.remove(event.id)
                    onChanged()
                }
            }
        }
    }

    private fun allow(number: String) {
        val rule = Rule(runtime.nextId(), true, RuleKind.ALLOW, MatcherType.EXACT, number, null, null)
        runtime.update { it.copy(rules = addRule(it.rules, rule)) }
        activity.toast(activity.getString(R.string.elbowsup_blocked_allowed, number.formatPhoneNumber()))
    }
}
