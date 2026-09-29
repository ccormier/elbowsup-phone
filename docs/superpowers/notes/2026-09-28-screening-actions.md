# Screening Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Silence action and split Reject into a quiet and a notifying action, wired through the engine, the screening service, the rule sheet, and History.

**Architecture:** `BlockAction` gains `SILENCE`, the existing `REJECT` becomes `REJECT_QUIET`, and a new `REJECT` maps to the notifying flags. `DecisionKind` mirrors the four actions; the screening service writes one blocked event for every reject-like or silence decision and maps the flags. History pairs silence events with missed or incoming rows and folds unanswered silenced attempts. No database migration; prototype data may be wiped.

**Tech Stack:** Kotlin 2.4.10, Jetpack Compose, Room, JUnit 4. Engine is pure Kotlin with no Android imports.

**Spec:** `docs/superpowers/specs/2026-09-28-screening-actions-design.md`

## Global Constraints

- minSdk 29, targetSdk 36, compileSdk 37. No new permissions and no new dependencies.
- `:engine` is pure Kotlin. No Android imports, ever.
- Fail open: any throw while screening allows the call. A blocked event written before a throwing response is deleted.
- The call path reads caches only; never Room or the contacts provider.
- Numbers compare in stored form (E.164 preferred).
- No schema change and no migration. Stored action strings: `REJECT_QUIET` (quiet, renamed from `REJECT`), `REJECT` (notifying), `SILENCE`.
- Exact labels: `Reject`, `Reject quietly`, `Silence`, `Answer and hang up`. New-rule default is `REJECT_QUIET`.
- `skipCallLog` stays false everywhere.
- Voicemail is never promised in copy.
- Build with `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` first.
- Unit tests: `./gradlew :engine:test :app:testDebugUnitTest`. App compile: `./gradlew :app:assembleDebug`.

## Review Focus

- An answered silenced call (an incoming row with a `SILENCE` event) must never fold into a blocked burst. Test in Task 3.
- A `SILENCE` event must pair only with missed or incoming rows, never with a blocked row. Test in Task 3.
- An answer-and-hangup rule without the dialer role must stay silent and record `REJECT_QUIET`. Test in Task 1.
- A stored action name the app no longer knows must still render in History instead of crashing. Test in Task 2.
- Silence must never be combined with disallow in any flag vector. Test in Task 1.

---

### Task 1: The action model end to end

**Files:**
- Modify: `engine/src/main/kotlin/app/elbowsup/blocker/engine/Rule.kt`
- Modify: `engine/src/main/kotlin/app/elbowsup/blocker/engine/RuleEngine.kt`
- Modify: `engine/src/main/kotlin/app/elbowsup/blocker/engine/ScreeningFlags.kt`
- Modify: `engine/src/main/kotlin/app/elbowsup/blocker/engine/Matcher.kt:92-96`
- Modify: `engine/src/test/kotlin/app/elbowsup/blocker/engine/TelecomPolicyTest.kt:83-88`
- Modify: `engine/src/test/kotlin/app/elbowsup/blocker/engine/RuleEngineTest.kt`
- Modify: `engine/src/test/kotlin/app/elbowsup/blocker/engine/MatcherTest.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/telecom/ElbowsUpScreeningService.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/RuleDraft.kt:110`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/HistoryChips.kt:54`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/RuleSheet.kt:107-111`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/BlockedMerge.kt:27-46`
- Modify: `app/src/test/kotlin/app/elbowsup/blocker/ui/HistoryChipsTest.kt:72-77`
- Modify: `app/src/test/kotlin/app/elbowsup/blocker/data/MappersTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `BlockAction { REJECT_QUIET, REJECT, SILENCE, ANSWER_HANGUP }` and `DecisionKind { ALLOW, REJECT_QUIET, REJECT, SILENCE, ANSWER_HANGUP }`. Later tasks rely on those exact names.

- [ ] **Step 1: Update the engine tests to the new names and behaviors**

In `TelecomPolicyTest.kt`, replace `flagsMatchTheSpec` and add the silence/disallow guard:

```kotlin
    @Test
    fun flagsMatchTheSpec() {
        assertEquals(ScreeningFlags(false, false, false, false, false), screeningFlags(DecisionKind.ALLOW))
        assertEquals(ScreeningFlags(true, true, false, false, true), screeningFlags(DecisionKind.REJECT_QUIET))
        assertEquals(ScreeningFlags(true, true, false, false, false), screeningFlags(DecisionKind.REJECT))
        assertEquals(ScreeningFlags(false, false, true, false, false), screeningFlags(DecisionKind.SILENCE))
        assertEquals(ScreeningFlags(false, false, true, false, false), screeningFlags(DecisionKind.ANSWER_HANGUP))
    }

    @Test
    fun silenceIsNeverCombinedWithDisallow() {
        for (kind in DecisionKind.entries) {
            val flags = screeningFlags(kind)
            assertFalse("$kind combines silence and disallow", flags.silence && flags.disallow)
        }
    }
```

In `RuleEngineTest.kt`:

```kotlin
    private fun block(id: Long, position: Int, matcher: MatcherType, pattern: String?, action: BlockAction = BlockAction.REJECT_QUIET, schedule: ScheduleKind = ScheduleKind.ALWAYS) =
        Rule(id, position, true, RuleKind.BLOCK, matcher, pattern, false, schedule, null, action)
```

Change `firstMatchWinsEvenWhenALaterPrefixIsLonger` to expect the quiet kind and use explicit actions:

```kotlin
        val rules = listOf(
            block(1, 0, MatcherType.PREFIX, "+1", BlockAction.REJECT_QUIET),
            block(2, 1, MatcherType.PREFIX, "+1415555", BlockAction.ANSWER_HANGUP),
        )
        val decision = evaluate("+14155551234", "A", context(rules))
        assertEquals(1L, decision.winningRuleId)
        assertEquals(DecisionKind.REJECT_QUIET, decision.kind)
```

Change `allowAboveABlockRingsAndBlockAboveAnAllowBlocks`'s block expectation to `DecisionKind.REJECT_QUIET`.

Replace `dialerLossRecordsReject` and add the two mapping tests:

```kotlin
    @Test
    fun dialerLossRecordsRejectQuietly() {
        val rules = listOf(block(1, 0, MatcherType.NO_NUMBER, null, BlockAction.ANSWER_HANGUP))
        val decision = evaluate(null, null, context(rules, dialerHeld = false))
        assertEquals(DecisionKind.REJECT_QUIET, decision.kind)
        assertEquals(BlockAction.REJECT_QUIET, decision.recordedAction)
    }

    @Test
    fun aSilenceRuleYieldsTheSilenceKind() {
        val rules = listOf(block(1, 0, MatcherType.PREFIX, "+1415", BlockAction.SILENCE))
        val decision = evaluate("+14155551234", null, context(rules))
        assertEquals(DecisionKind.SILENCE, decision.kind)
        assertEquals(BlockAction.SILENCE, decision.recordedAction)
    }

    @Test
    fun aNotifyingRejectRuleYieldsTheRejectKind() {
        val rules = listOf(block(1, 0, MatcherType.PREFIX, "+1415", BlockAction.REJECT))
        val decision = evaluate("+14155551234", null, context(rules))
        assertEquals(DecisionKind.REJECT, decision.kind)
        assertEquals(BlockAction.REJECT, decision.recordedAction)
    }
```

In `MatcherTest.kt`, add:

```kotlin
    @Test
    fun ruleSummaryNamesEveryAction() {
        assertEquals("Block prefix +1415, reject quietly", ruleSummary(rule(MatcherType.PREFIX, "+1415", action = BlockAction.REJECT_QUIET)))
        assertEquals("Block prefix +1415, reject", ruleSummary(rule(MatcherType.PREFIX, "+1415", action = BlockAction.REJECT)))
        assertEquals("Block prefix +1415, silence", ruleSummary(rule(MatcherType.PREFIX, "+1415", action = BlockAction.SILENCE)))
        assertEquals("Block prefix +1415, answer and hang up", ruleSummary(rule(MatcherType.PREFIX, "+1415", action = BlockAction.ANSWER_HANGUP)))
    }
```

- [ ] **Step 2: Run the engine tests to verify they fail**

Run:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew :engine:test
```

Expected: compilation fails — `BlockAction.REJECT_QUIET` and `DecisionKind.SILENCE` do not exist.

- [ ] **Step 3: Implement the engine changes**

`Rule.kt` line 6:

```kotlin
enum class BlockAction { REJECT_QUIET, REJECT, SILENCE, ANSWER_HANGUP }
```

`RuleEngine.kt` line 3 and `decisionFor`:

```kotlin
enum class DecisionKind { ALLOW, REJECT_QUIET, REJECT, SILENCE, ANSWER_HANGUP }
```

```kotlin
private fun decisionFor(rule: Rule, dialerHeld: Boolean): Decision {
    val summary = ruleSummary(rule)
    if (rule.kind == RuleKind.ALLOW) return Decision(DecisionKind.ALLOW, rule.id, summary, null)
    val requested = rule.action ?: BlockAction.REJECT_QUIET
    val recorded = if (!dialerHeld && requested == BlockAction.ANSWER_HANGUP) BlockAction.REJECT_QUIET else requested
    val kind = when (recorded) {
        BlockAction.REJECT_QUIET -> DecisionKind.REJECT_QUIET
        BlockAction.REJECT -> DecisionKind.REJECT
        BlockAction.SILENCE -> DecisionKind.SILENCE
        BlockAction.ANSWER_HANGUP -> DecisionKind.ANSWER_HANGUP
    }
    return Decision(kind, rule.id, summary, recorded)
}
```

`ScreeningFlags.kt` lines 14-18:

```kotlin
fun screeningFlags(kind: DecisionKind): ScreeningFlags = when (kind) {
    DecisionKind.ALLOW -> ScreeningFlags(false, false, false, false, false)
    DecisionKind.REJECT_QUIET -> ScreeningFlags(true, true, false, false, true)
    DecisionKind.REJECT -> ScreeningFlags(true, true, false, false, false)
    DecisionKind.SILENCE -> ScreeningFlags(false, false, true, false, false)
    DecisionKind.ANSWER_HANGUP -> ScreeningFlags(false, false, true, false, false)
}
```

`Matcher.kt` lines 93-95:

```kotlin
    val action = if (rule.kind == RuleKind.BLOCK) {
        when (rule.action ?: BlockAction.REJECT_QUIET) {
            BlockAction.REJECT_QUIET -> ", reject quietly"
            BlockAction.REJECT -> ", reject"
            BlockAction.SILENCE -> ", silence"
            BlockAction.ANSWER_HANGUP -> ", answer and hang up"
        }
    } else ""
```

- [ ] **Step 4: Run the engine tests to verify they pass**

Run:

```bash
./gradlew :engine:test
```

Expected: PASS.

- [ ] **Step 5: Update the app call sites so the app compiles and behavior is preserved**

`ElbowsUpScreeningService.kt`: rename `rejectEventId` to `pendingEventId` everywhere in the file, and replace the `when (decision.kind)` block with:

```kotlin
            when (decision.kind) {
                DecisionKind.ALLOW -> respondAllow()
                DecisionKind.ANSWER_HANGUP -> {
                    val callId = callDetails.id
                    if (callId.isNullOrBlank()) {
                        respondAllow()
                        return
                    }
                    app.handoffStore.put(
                        callId,
                        nowMillis,
                        ruleId = decision.winningRuleId,
                        ruleSummary = decision.ruleSummary,
                    )
                    try {
                        respond(flags)
                    } catch (e: Exception) {
                        app.handoffStore.claim(callId, nowMillis)
                        throw e
                    }
                }
                DecisionKind.REJECT_QUIET,
                DecisionKind.REJECT,
                DecisionKind.SILENCE -> {
                    val event = BlockedEventEntity(
                        timeEpochMillis = nowMillis,
                        number = snapshot.storedNumber,
                        displayName = snapshot.displayName,
                        action = decision.recordedAction?.name ?: BlockAction.REJECT_QUIET.name,
                        ruleId = decision.winningRuleId,
                        ruleSummary = decision.ruleSummary ?: "",
                    )
                    pendingEventId = runBlocking(Dispatchers.IO) {
                        app.database.blocked().insert(event)
                    }
                    respond(flags)
                    pendingEventId = null
                }
            }
```

In the `catch (_: Throwable)` block, rename the local to `pending` and delete by it.

`RuleDraft.kt` line 110: `action ?: BlockAction.REJECT_QUIET`.

`HistoryChips.kt` line 54: `RuleSheetPrefill(ScheduleKind.ALWAYS, BlockAction.REJECT_QUIET)`.

`RuleSheet.kt` lines 107-111: keep the labels for now, but the quiet action becomes the offered value:

```kotlin
                        value = if (draft.action == BlockAction.ANSWER_HANGUP) "Answer and hang up" else "Reject",
                        options = listOf(
                            Choice("Reject", BlockAction.REJECT_QUIET),
                            Choice("Answer and hang up", BlockAction.ANSWER_HANGUP, enabled = dialerHeld),
                        ),
```

`BlockedMerge.kt`: accept the quiet action and label both rejects as `Reject` for now (Task 2 gives them final labels):

```kotlin
        val expectedType = when (event.action) {
            BlockAction.REJECT.name, BlockAction.REJECT_QUIET.name -> CallLog.Calls.BLOCKED_TYPE
            BlockAction.ANSWER_HANGUP.name -> CallLog.Calls.INCOMING_TYPE
            else -> continue
        }
```

```kotlin
fun blockedActionLabel(action: String): String = when (action) {
    BlockAction.REJECT.name -> "Reject"
    BlockAction.REJECT_QUIET.name -> "Reject"
    BlockAction.ANSWER_HANGUP.name -> "Answer and hang up"
    else -> action
}
```

- [ ] **Step 6: Update the app tests**

`HistoryChipsTest.kt` line 76: expect `BlockAction.REJECT_QUIET`.

`MappersTest.kt`, add:

```kotlin
    @Test
    fun everyActionRoundTrips() {
        for (action in BlockAction.entries) {
            val rule = app.elbowsup.blocker.engine.Rule(
                id = 1,
                position = 0,
                enabled = true,
                kind = RuleKind.BLOCK,
                matcher = MatcherType.PREFIX,
                pattern = "+1415",
                emptyNameOnly = false,
                schedule = ScheduleKind.ALWAYS,
                customWindow = null,
                action = action,
            )
            assertEquals(action, rule.toEntity().toRule().action)
        }
    }
```

- [ ] **Step 7: Run every unit test and the app compile**

Run:

```bash
./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add engine app
git commit -m "feat: add the silence and notifying reject actions"
```

---

### Task 2: Action labels and the rule-sheet menu

**Files:**
- Create: `app/src/main/kotlin/app/elbowsup/blocker/ui/ActionText.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/RuleSheet.kt:104-115`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/BlockedMerge.kt:43-47`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/HistoryTab.kt:200,249`
- Modify: `app/src/test/kotlin/app/elbowsup/blocker/ui/AttemptBurstsTest.kt:156-161`
- Create: `app/src/test/kotlin/app/elbowsup/blocker/ui/ActionTextTest.kt`

**Interfaces:**
- Consumes: `BlockAction` from Task 1.
- Produces: `actionLabel(BlockAction?)`, `blockedActionLabel(String)`, `blockedBadge(String)`, `blockedByTitle(String)` in package `app.elbowsup.blocker.ui`.

- [ ] **Step 1: Write the failing label tests**

Create `ActionTextTest.kt`:

```kotlin
package app.elbowsup.blocker.ui

import app.elbowsup.blocker.engine.BlockAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionTextTest {
    @Test
    fun everyActionHasALabel() {
        assertEquals("Reject", actionLabel(BlockAction.REJECT))
        assertEquals("Reject quietly", actionLabel(BlockAction.REJECT_QUIET))
        assertEquals("Silence", actionLabel(BlockAction.SILENCE))
        assertEquals("Answer and hang up", actionLabel(BlockAction.ANSWER_HANGUP))
        assertEquals("Reject quietly", actionLabel(null))
    }

    @Test
    fun aStoredActionNameStillRenders() {
        assertEquals("Reject", blockedActionLabel(BlockAction.REJECT.name))
        assertEquals("Reject quietly", blockedActionLabel(BlockAction.REJECT_QUIET.name))
        assertEquals("Silence", blockedActionLabel(BlockAction.SILENCE.name))
        assertEquals("Answer and hang up", blockedActionLabel(BlockAction.ANSWER_HANGUP.name))
        assertEquals("LEGACY", blockedActionLabel("LEGACY"))
    }

    @Test
    fun silenceReadsAsSilencedNotBlocked() {
        assertEquals("Silenced", blockedBadge(BlockAction.SILENCE.name))
        assertEquals("Silenced by ElbowsUp", blockedByTitle(BlockAction.SILENCE.name))
        assertEquals("Blocked", blockedBadge(BlockAction.REJECT.name))
        assertEquals("Blocked by ElbowsUp", blockedByTitle(BlockAction.REJECT.name))
    }
}
```

In `AttemptBurstsTest.kt`, replace `blockedActionLabelNamesBothActions` with the part that is not about labels:

```kotlin
    @Test
    fun aRowWithoutAMatchIsNotABlockedAttempt() {
        assertFalse(HistoryRow(1, null, null, null, 0, 0, CallLog.Calls.INCOMING_TYPE).isBlockedAttempt(emptyMap()))
    }
```

- [ ] **Step 2: Run the app tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: compilation fails — `actionLabel`, `blockedBadge`, and `blockedByTitle` do not exist.

- [ ] **Step 3: Create ActionText.kt and remove the old label function**

Create `ActionText.kt`:

```kotlin
package app.elbowsup.blocker.ui

import app.elbowsup.blocker.engine.BlockAction

fun actionLabel(action: BlockAction?): String = when (action) {
    BlockAction.REJECT -> "Reject"
    BlockAction.REJECT_QUIET -> "Reject quietly"
    BlockAction.SILENCE -> "Silence"
    BlockAction.ANSWER_HANGUP -> "Answer and hang up"
    null -> "Reject quietly"
}

fun blockedActionLabel(action: String): String =
    BlockAction.entries.firstOrNull { it.name == action }?.let(::actionLabel) ?: action

fun blockedBadge(action: String): String =
    if (action == BlockAction.SILENCE.name) "Silenced" else "Blocked"

fun blockedByTitle(action: String): String =
    if (action == BlockAction.SILENCE.name) "Silenced by ElbowsUp" else "Blocked by ElbowsUp"
```

Delete `blockedActionLabel` from `BlockedMerge.kt`.

- [ ] **Step 4: Use the labels in the sheet and History**

`RuleSheet.kt`:

```kotlin
                        value = actionLabel(draft.action),
                        options = listOf(
                            Choice("Reject quietly", BlockAction.REJECT_QUIET),
                            Choice("Reject", BlockAction.REJECT),
                            Choice("Silence", BlockAction.SILENCE),
                            Choice("Answer and hang up", BlockAction.ANSWER_HANGUP, enabled = dialerHeld),
                        ),
```

`HistoryTab.kt` line 200: `Text(blockedByTitle(event.action))`.

`HistoryTab.kt` line 249: `val label = if (event != null) "${blockedBadge(event.action)} · ${blockedActionLabel(event.action)}" else callTypeLabel(newest.type)`.

- [ ] **Step 5: Run the app tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat: offer all four block actions and label them"
```

---

### Task 3: History pairing and folding for silence

**Files:**
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/BlockedMerge.kt:20-41`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/AttemptBursts.kt:35-37`
- Modify: `app/src/test/kotlin/app/elbowsup/blocker/ui/AttemptBurstsTest.kt`

**Interfaces:**
- Consumes: `actionLabel` and friends from Task 2, `BlockAction.SILENCE` from Task 1.
- Produces: `matchBlockedEvents` accepts a set of row types per action; `HistoryRow.isBlockedAttempt` folds unanswered silence.

- [ ] **Step 1: Write the failing History tests**

Add to `AttemptBurstsTest.kt`:

```kotlin
    @Test
    fun aSilenceEventMatchesMissedAndIncomingRows() {
        val missed = row(1, 5_000, type = CallLog.Calls.MISSED_TYPE)
        val incoming = row(2, 9_000, type = CallLog.Calls.INCOMING_TYPE, duration = 5)

        val matches = matchBlockedEvents(
            listOf(missed, incoming),
            listOf(event(7, 5_100, action = BlockAction.SILENCE), event(8, 9_100, action = BlockAction.SILENCE)),
        )

        assertEquals(7L, matches[1L]?.id)
        assertEquals(8L, matches[2L]?.id)
    }

    @Test
    fun aSilenceEventNeverClaimsABlockedRow() {
        val blocked = row(1, 5_000, type = CallLog.Calls.BLOCKED_TYPE)
        val matches = matchBlockedEvents(listOf(blocked), listOf(event(7, 5_100, action = BlockAction.SILENCE)))
        assertTrue(matches.isEmpty())
    }

    @Test
    fun unansweredSilenceRowsFoldButAnAnsweredOneDoesNot() {
        val missedRows = listOf(
            row(2, 7_500, type = CallLog.Calls.MISSED_TYPE),
            row(1, 5_000, type = CallLog.Calls.MISSED_TYPE),
        )
        val missedEvents = listOf(
            event(2, 7_600, action = BlockAction.SILENCE),
            event(1, 5_100, action = BlockAction.SILENCE),
        )
        assertEquals(1, historyBursts(missedRows, matchBlockedEvents(missedRows, missedEvents)).size)

        val answeredRows = listOf(
            row(2, 7_500, type = CallLog.Calls.INCOMING_TYPE, duration = 4),
            row(1, 5_000, type = CallLog.Calls.MISSED_TYPE),
        )
        val answeredEvents = listOf(
            event(2, 7_600, action = BlockAction.SILENCE),
            event(1, 5_100, action = BlockAction.SILENCE),
        )
        assertEquals(2, historyBursts(answeredRows, matchBlockedEvents(answeredRows, answeredEvents)).size)
    }
```

- [ ] **Step 2: Run the app tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: `aSilenceEventMatchesMissedAndIncomingRows` fails because `SILENCE` has no expected row type; `unansweredSilenceRowsFoldButAnAnsweredOneDoesNot` fails because silence rows do not fold.

- [ ] **Step 3: Implement pairing and folding**

In `BlockedMerge.kt`, replace the `expectedType` lookup with a set:

```kotlin
        val expectedTypes = expectedRowTypes(event.action)
        if (expectedTypes.isEmpty()) continue
        val candidate = rows
            .filter { it.id !in usedRows && it.type in expectedTypes && it.storedNumber == event.number }
            .minByOrNull { abs(it.startEpochMillis - event.timeEpochMillis) }
            ?: continue
```

and add:

```kotlin
private fun expectedRowTypes(action: String): Set<Int> = when (action) {
    BlockAction.REJECT.name, BlockAction.REJECT_QUIET.name -> setOf(CallLog.Calls.BLOCKED_TYPE)
    BlockAction.SILENCE.name -> setOf(CallLog.Calls.MISSED_TYPE, CallLog.Calls.INCOMING_TYPE)
    BlockAction.ANSWER_HANGUP.name -> setOf(CallLog.Calls.INCOMING_TYPE)
    else -> emptySet()
}
```

In `AttemptBursts.kt`:

```kotlin
fun HistoryRow.isBlockedAttempt(matches: Map<Long, BlockedEventEntity>): Boolean {
    if (storedNumber == null) return false
    if (type == CallLog.Calls.BLOCKED_TYPE) return true
    val action = matches[id]?.action
    return action == BlockAction.ANSWER_HANGUP.name ||
        (action == BlockAction.SILENCE.name && type == CallLog.Calls.MISSED_TYPE)
}
```

- [ ] **Step 4: Run the app tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat: pair and fold silenced calls in history"
```

---

### Task 4: Sync the binding design spec

**Files:**
- Modify: `docs/superpowers/specs/2026-09-26-elbowsup-call-blocker-design.md`

**Interfaces:**
- Consumes: the behavior implemented in Tasks 1-3.
- Produces: a main spec that matches the code.

- [ ] **Step 1: Apply the amendments**

Make these edits to `2026-09-26-elbowsup-call-blocker-design.md`:

- Line 13: replace `and they do not raise a missed-call notification` with `and they raise a missed-call notification only when the rule asks for one`.
- Line 15: replace `A block rule can reject the call, or answer and hang up so the caller usually does not reach voicemail.` with `A block rule can reject the call, reject quietly, silence it, or answer and hang up so the caller usually does not reach voicemail.`
- Line 22: remove `, and no user-facing "silence and let it ring" action`.
- Line 181 (Rules tab), after `Each row can be edited or deleted.`, add: `The rule sheet's action menu offers Reject quietly (the default for a new block rule), Reject, Silence, and Answer and hang up.`
- Replace the whole `## Telecom actions` section with:

```markdown
## Telecom actions

**Allow.** Empty `CallResponse`. Show the incoming screen.

**Reject quietly.** Write the blocked event, then respond with disallow, reject, and skip notification. Do not also silence; silence is illegal when the call is disallowed. The caller usually goes to voicemail, and the platform keeps its own record of the blocked call. If the response throws, delete the blocked event.

**Reject.** The same flags as Reject quietly, with skip notification off, so the platform raises its missed-call notification. Write and roll back the blocked event the same way.

**Silence.** Respond with silence only, never disallow. The call still goes to the dialer: the incoming screen appears and can be answered, but the phone plays no ringtone and does not vibrate, because ElbowsUp declares `IN_CALL_SERVICE_RINGING=false` and Telecom's Ringer owns both. The platform forces the call-log row and the missed-call notification. Silence cannot be undone. Write the blocked event before responding and delete it if the response throws.

**Answer and hang up.** Do not disallow the call, or the dialer never sees it. Respond with silence and without skip-log (skip-log applies only when disallowed). Store an in-memory handoff keyed by that screening call's telecom id (`Call.Details.getId()`), plus the time. Do not key it by number. Do not write it to disk. If the id is missing, do not answer-and-hangup; treat the call as allowed for the in-call UI, even though screening may already have silenced it.

When the in-call service sees a ringing call, it answer-and-hangups only if an unclaimed handoff exists for that same telecom id and is younger than 10 seconds. It then claims the handoff, does not show an incoming screen or post an incoming-call notification, answers, disconnects, and writes the blocked event. The platform's call-log row is left in place. A later call, including an allowed call or an emergency call with the same number, has a different id and must not be answered by this handoff. No-number calls do not share a token.

Silence cannot be undone. If the handoff is missed or answer fails, the call may already be silent. Show the incoming screen anyway so the user can answer. Do not answer late, and do not describe this path as a normal ring.

If disconnect fails after answer, show the normal in-call screen so the user can hang up.

Voicemail is the carrier's decision, not the app's. Reject and Reject quietly reach voicemail where the carrier forwards declined calls; Silence reaches it through no-answer forwarding; Answer and hang up never does. Copy must not promise voicemail.
```

- Line 173 History paragraph, after `A row from another number between two attempts ends the run.`, add: `A silenced call leaves a normal missed or incoming row; its blocked event pairs with it the same way, and a run of unanswered silenced attempts folds like a rejected run, while an answered silenced call never folds.`
- Line 187 Settings paragraph: replace `The answer-hangup action is disabled in the rule sheet while ElbowsUp is not the phone app, with the same warning.` with `The answer-hangup action is disabled in the rule sheet while ElbowsUp is not the phone app, with the same warning. The other three block actions are always available, and a new block rule defaults to Reject quietly.`
- Line 191 Stored data, Rule bullet: replace `action or null,` with `action or null (REJECT_QUIET, REJECT, SILENCE, or ANSWER_HANGUP for a block rule),`
- Line 205 Failures, after the `Handoff missed, answer fails, or the screening call has no telecom id` bullet, add: `Silence is irreversible once the response is sent. Voicemail is not promised: the carrier decides whether a decline or a no-answer reaches voicemail.`
- Line 226 Mapping tests: replace the whole sentence with `Mapping tests: allow, reject quietly, reject, silence, and answer-and-hang-up produce the CallResponse flags in this spec, including that silence is never combined with disallow, reject quietly does not set silence or skip the call log, and answer-and-hang-up does not set disallow. A handoff for telecom id A does not answer a ringing call with id B.`
- Line 230 Folding tests: after `including an answer-and-hangup incoming row that matched a blocked event;`, add `including unanswered silenced rows that matched blocked events while an answered silenced row does not fold;`
- Line 232 Device checks: after `answer-and-hangup lands in History with its action and rule summary;`, add `silence plays no ringtone and no vibration while the incoming screen still answers; a rejecting rule reaches voicemail where the carrier forwards declines and raises a missed-call notification; reject quietly matches the old reject exactly;`

- [ ] **Step 2: Verify the spec diff**

Run:

```bash
git diff docs/superpowers/specs/2026-09-26-elbowsup-call-blocker-design.md
```

Expected: only the sections above changed; no leftover "no user-facing" silence ban; the action names match the code exactly.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-09-26-elbowsup-call-blocker-design.md
git commit -m "docs: sync the design spec with the screening actions"
```

---

### Task 5: Device verification

**Files:** none. This task is a manual pass on a phone, per `AGENTS.md`.

- [ ] **Step 1: Install and grant roles**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew :app:installDebug
```

On the phone: grant the dialer and call-screening roles and the contacts permission. Wipe app data first if prototype rules exist, or re-save them, because the stored `REJECT` value now means the notifying action.

- [ ] **Step 2: Exercise each action**

From another phone, for each of these rules:

- **Silence:** no ringtone, no vibration, the incoming screen appears and answers; if unanswered, a missed-call notification and a normal call-log row.
- **Reject:** the caller reaches voicemail (where the carrier forwards declines) and the phone shows a missed-call notification; the call-log row is blocked.
- **Reject quietly:** voicemail, no notification, blocked row — identical to the old Reject.
- **Answer and hang up:** no voicemail, no notification, a short incoming row.
- **Dialer loss:** revoke the dialer role and confirm an answer-hangup rule behaves as Reject quietly, with the event recording it.

- [ ] **Step 3: Regression pass**

Emergency numbers place; a visible contact rings under a matching prefix; pause allows; contacts withheld allows; the settings shortcuts open the system pages. Restore the previous default dialer afterwards.
