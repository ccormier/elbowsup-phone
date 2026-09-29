package com.keejii.elbowsup.ui

import androidx.appcompat.app.AlertDialog
import com.keejii.elbowsup.core.BlockAction
import com.keejii.elbowsup.core.DraftResult
import com.keejii.elbowsup.core.MatcherType
import com.keejii.elbowsup.core.Rule
import com.keejii.elbowsup.core.RuleDraft
import com.keejii.elbowsup.core.RuleKind
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.models.RadioItem
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialogElbowsupRuleBinding

private val NUMBER_MATCHERS = setOf(MatcherType.EXACT, MatcherType.PREFIX)
private val PATTERN_MATCHERS = NUMBER_MATCHERS + MatcherType.NAME_WILDCARD

/**
 * Adds or edits a rule. [onSave] returns false when the rule clashes with another one, and the
 * dialog then stays open. [onDelete] is null for a new rule.
 */
class RuleDialog(
    private val activity: SimpleActivity,
    private val draft: RuleDraft,
    private val answerHangupAvailable: Boolean,
    private val region: String?,
    private val onSave: (Rule) -> Boolean,
    private val onDelete: (() -> Unit)?,
) {
    private val binding = DialogElbowsupRuleBinding.inflate(activity.layoutInflater)
    private val window = WindowFields(
        activity,
        binding.elbowsupRuleWindow,
        draft.days,
        draft.startMinute,
        draft.endMinute,
    )
    private var matcher = draft.matcher

    init {
        showDraft()
        binding.elbowsupRuleMatcher.setOnClickListener { chooseMatcher() }
        binding.elbowsupRuleKind.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        binding.elbowsupRuleWhen.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        updateVisibility()

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
        if (onDelete != null) builder.setNeutralButton(org.fossify.commons.R.string.delete, null)
        val title = if (draft.id == 0L) R.string.elbowsup_rule_new else R.string.elbowsup_rule_edit
        activity.setupDialogStuff(binding.root, builder, title) { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(dialog) }
            if (onDelete != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    onDelete.invoke()
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showDraft() = binding.apply {
        (if (draft.kind == RuleKind.BLOCK) elbowsupKindBlock else elbowsupKindAllow).isChecked = true
        elbowsupRulePattern.setText(draft.patternText)
        elbowsupRuleEmptyNameOnly.isChecked = draft.emptyNameOnly
        (if (draft.customWindow) elbowsupWhenWindow else elbowsupWhenAlways).isChecked = true
        actionButtons().forEach { (action, button) -> button.isChecked = action == draft.action }
        elbowsupActionAnswerHangup.isEnabled = answerHangupAvailable
        elbowsupActionNote.beVisibleIf(!answerHangupAvailable)
        elbowsupRuleDialogEnabled.isChecked = draft.enabled
    }

    private fun actionButtons() = binding.run {
        listOf(
            BlockAction.REJECT_QUIET to elbowsupActionRejectQuiet,
            BlockAction.REJECT to elbowsupActionReject,
            BlockAction.SILENCE to elbowsupActionSilence,
            BlockAction.ANSWER_HANGUP to elbowsupActionAnswerHangup,
        )
    }

    private fun chooseMatcher() {
        val items = ArrayList(MatcherType.entries.map { RadioItem(it.ordinal, activity.matcherLabel(it)) })
        RadioGroupDialog(activity, items, matcher.ordinal) { selected ->
            matcher = MatcherType.entries[selected as Int]
            updateVisibility()
        }
    }

    private fun updateVisibility() = binding.apply {
        elbowsupRuleMatcher.text = activity.matcherLabel(matcher)
        elbowsupRulePatternLayout.beVisibleIf(matcher in PATTERN_MATCHERS)
        elbowsupRulePatternLayout.hint = activity.getString(
            when (matcher) {
                MatcherType.EXACT -> R.string.elbowsup_hint_exact
                MatcherType.NAME_WILDCARD -> R.string.elbowsup_hint_name
                else -> R.string.elbowsup_hint_prefix
            },
        )
        elbowsupRuleNameNote.beVisibleIf(matcher == MatcherType.NAME_WILDCARD || matcher == MatcherType.EMPTY_NAME)
        elbowsupRuleEmptyNameOnly.beVisibleIf(matcher in NUMBER_MATCHERS)
        elbowsupRuleActionHolder.beVisibleIf(elbowsupKindBlock.isChecked)
        window.setVisible(elbowsupWhenWindow.isChecked)
    }

    private fun readDraft() = binding.let {
        draft.copy(
            enabled = it.elbowsupRuleDialogEnabled.isChecked,
            kind = if (it.elbowsupKindBlock.isChecked) RuleKind.BLOCK else RuleKind.ALLOW,
            matcher = matcher,
            patternText = it.elbowsupRulePattern.text?.toString().orEmpty(),
            emptyNameOnly = it.elbowsupRuleEmptyNameOnly.isChecked,
            customWindow = it.elbowsupWhenWindow.isChecked,
            days = window.days,
            startMinute = window.startMinute,
            endMinute = window.endMinute,
            action = actionButtons().firstOrNull { (_, button) -> button.isChecked }?.first ?: BlockAction.REJECT_QUIET,
        )
    }

    private fun save(dialog: AlertDialog) {
        when (val result = readDraft().toRule(region)) {
            is DraftResult.Invalid -> activity.toast(activity.problemText(result.problem))
            is DraftResult.Valid ->
                if (onSave(result.rule)) dialog.dismiss() else activity.toast(R.string.elbowsup_error_duplicate)
        }
    }
}
