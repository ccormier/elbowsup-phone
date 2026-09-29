package com.keejii.elbowsup.core

/** Two rules are the same rule when they match the same calls the same way; the action does not count. */
private fun sameIdentity(left: Rule, right: Rule): Boolean =
    left.kind == right.kind &&
        left.matcher == right.matcher &&
        left.pattern == right.pattern &&
        left.emptyNameOnly == right.emptyNameOnly &&
        left.window == right.window

/**
 * Adds a rule: an allow goes to the top and a block to the bottom. If the same rule already exists it
 * is updated in place (enabled and action only) and does not move.
 */
fun addRule(rules: List<Rule>, incoming: Rule): List<Rule> {
    val existing = rules.indexOfFirst { sameIdentity(it, incoming) }
    return when {
        existing >= 0 -> rules.mapIndexed { index, rule ->
            if (index == existing) rule.copy(enabled = incoming.enabled, action = incoming.action) else rule
        }
        incoming.kind == RuleKind.ALLOW -> listOf(incoming) + rules
        else -> rules + incoming
    }
}

/** Replaces the rule with the same id, or null if it is gone or now duplicates a different rule. */
fun replaceRule(rules: List<Rule>, edited: Rule): List<Rule>? {
    if (rules.none { it.id == edited.id }) return null
    if (rules.any { it.id != edited.id && sameIdentity(it, edited) }) return null
    return rules.map { if (it.id == edited.id) edited else it }
}

fun deleteRule(rules: List<Rule>, id: Long): List<Rule> = rules.filter { it.id != id }

/** Moves a rule by [offset] places (negative is up); at either end it stays put. */
fun moveRule(rules: List<Rule>, id: Long, offset: Int): List<Rule> {
    val from = rules.indexOfFirst { it.id == id }
    val to = from + offset
    if (from < 0 || to !in rules.indices) return rules
    return rules.toMutableList().apply { add(to, removeAt(from)) }
}

fun setRuleEnabled(rules: List<Rule>, id: Long, enabled: Boolean): List<Rule> =
    rules.map { if (it.id == id) it.copy(enabled = enabled) else it }
