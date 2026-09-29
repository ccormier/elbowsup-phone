package com.keejii.elbowsup.ui

import android.os.Build
import android.view.LayoutInflater
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.Rule
import com.keejii.elbowsup.core.RuleDraft
import com.keejii.elbowsup.core.addRule
import com.keejii.elbowsup.core.canAnswerHangup
import com.keejii.elbowsup.core.deleteRule
import com.keejii.elbowsup.core.draftOf
import com.keejii.elbowsup.core.moveRule
import com.keejii.elbowsup.core.replaceRule
import com.keejii.elbowsup.core.ruleSummary
import com.keejii.elbowsup.core.setRuleEnabled
import com.keejii.elbowsup.telecom.currentRegion
import com.keejii.elbowsup.telecom.dialerRoleHeld
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ActivityElbowsupBlockerBinding
import org.fossify.phone.databinding.ItemElbowsupRuleBinding

/** The ordered rule list: rows, reordering, and the add/edit/delete flows. */
class RulesSection(
    private val activity: SimpleActivity,
    private val binding: ActivityElbowsupBlockerBinding,
    private val runtime: BlockerRuntime,
) {
    init {
        binding.elbowsupAddRule.setOnClickListener { edit(RuleDraft()) }
    }

    fun render(rules: List<Rule>) {
        binding.elbowsupRulesEmpty.beVisibleIf(rules.isEmpty())
        binding.elbowsupRulesList.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        rules.forEachIndexed { index, rule ->
            val row = ItemElbowsupRuleBinding.inflate(inflater, binding.elbowsupRulesList, false)
            row.elbowsupRuleTitle.text = ruleSummary(rule)
            row.elbowsupRuleSubtitle.text =
                rule.window?.let { windowText(it) } ?: activity.getString(R.string.elbowsup_rule_always)
            row.elbowsupRuleEnabled.isChecked = rule.enabled
            row.elbowsupRuleEnabled.setOnCheckedChangeListener { _, checked ->
                runtime.update { it.copy(rules = setRuleEnabled(it.rules, rule.id, checked)) }
            }
            row.elbowsupRuleUp.beVisibleIf(index > 0)
            row.elbowsupRuleDown.beVisibleIf(index < rules.lastIndex)
            row.elbowsupRuleUp.setOnClickListener { move(rule, -1) }
            row.elbowsupRuleDown.setOnClickListener { move(rule, 1) }
            val iconColor = activity.getProperTextColor()
            listOf(row.elbowsupRuleUp, row.elbowsupRuleDown).forEach { it.applyColorFilter(iconColor) }
            row.elbowsupRuleHolder.setOnClickListener { edit(draftOf(rule)) }
            binding.elbowsupRulesList.addView(row.root)
        }
    }

    private fun move(rule: Rule, offset: Int) {
        runtime.update { it.copy(rules = moveRule(it.rules, rule.id, offset)) }
    }

    private fun edit(draft: RuleDraft) {
        RuleDialog(
            activity = activity,
            draft = draft,
            answerHangupAvailable = canAnswerHangup(Build.VERSION.SDK_INT, activity.dialerRoleHeld()),
            region = activity.currentRegion(),
            onSave = { save(it) },
            onDelete = if (draft.id == 0L) null else ({ delete(draft.id) }),
        )
    }

    private fun save(rule: Rule): Boolean {
        val current = runtime.snapshot().rules
        val next = if (rule.id == 0L) addRule(current, rule.copy(id = runtime.nextId())) else replaceRule(current, rule)
        if (next == null) return false
        runtime.update { it.copy(rules = next) }
        return true
    }

    private fun delete(id: Long) {
        runtime.update { it.copy(rules = deleteRule(it.rules, id)) }
    }
}
