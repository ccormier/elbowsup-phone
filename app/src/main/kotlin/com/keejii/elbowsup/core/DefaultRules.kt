package com.keejii.elbowsup.core

/** The caller ID names the carrier uses for calls it believes are unwanted. */
private val DEFAULT_BLOCKED_NAMES = listOf("Likely Spam", "Likely Fraud")

/**
 * What a fresh install starts with: one block rule for each carrier label, written out in full and
 * not as a wildcard, so they match those names and nothing broader. Rules that already exist are
 * left exactly as they are.
 */
fun withDefaultRules(existing: List<Rule>, nextId: () -> Long): List<Rule> =
    existing.ifEmpty {
        DEFAULT_BLOCKED_NAMES.map { name ->
            Rule(
                id = nextId(),
                enabled = true,
                kind = RuleKind.BLOCK,
                matcher = MatcherType.NAME_WILDCARD,
                pattern = name,
                window = null,
                action = BlockAction.REJECT_QUIET,
            )
        }
    }
