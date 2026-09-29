# ElbowsUp Call Blocker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build ElbowsUp, an offline Android phone app that blocks incoming calls with user rules and can answer-and-hangup matched calls.

**Architecture:** A pure Kotlin `:engine` module decides allow, reject, or answer-and-hangup. The `:app` module holds the dialer and call-screening roles, keeps rules and contacts in memory for the call path, and shows history, blocked calls, and the rule list.

**Tech Stack:** Kotlin 2.4.10, Android Gradle Plugin 9.4.0, Gradle 9.6.0, Jetpack Compose BOM 2026.09.00, Room 2.8.5, libphonenumber 9.0.40, JUnit 4. Min SDK 29, compileSdk 36, targetSdk 36. Application id `app.elbowsup.blocker`.

**Spec:** `docs/superpowers/specs/2026-09-26-elbowsup-call-blocker-design.md`

## Global Constraints

- No `INTERNET` permission, and no network calls. `android:allowBackup="false"`.
- Minimum SDK 29. Do not add Google Play Services.
- Outgoing calls are never blocked. Emergency numbers are never blocked. Use the platform emergency-number check, not a hardcoded list.
- Contacts always allow, before the rule list. A block rule cannot override a visible contact.
- If contacts cannot be read, or the visible set is empty and the empty-book override is off, allow every call and do not run rules.
- A missing rule cache or contact cache on the call path allows the call. Do not query Room or the contacts provider inside `onScreenCall`.
- Pause disables the blocker, including Sleep and Work rules. The dialer stays up.
- Reject uses disallow + reject + skip notification, does not set silence, and leaves the call log to the platform.
- Answer-and-hangup uses silence, does not disallow, and is keyed by `Call.Details.getId()`, never by phone number.
- Silence cannot be undone. A missed handoff shows the incoming screen and does not answer.
- New allow rules insert at the top. New block rules insert at the bottom. A duplicate identity updates in place and does not move.
- Monday–Friday 22:00–07:00 is active Saturday 03:00 and inactive Monday 03:00. Start inclusive, end exclusive.
- Name wildcard and name regex do not match a blank name. Number regex must match the entire stored number.
- A regex that throws or exceeds 50ms is skipped. Evaluation continues.
- Before the setup-completed flag is set, screening responds allow.
- After setup, dialer loss turns answer-and-hangup into reject and the blocked event records reject.

## Review Focus

These are the inputs most likely to block the wrong person or let the wrong call through. Each has a test in the task that owns the code.

- A blank or whitespace caller name is absent: an empty-name rule matches it, and a name wildcard `*` does not. Task 4.
- A contact saved as `(415) 555-1234` is the same stored number as `+14155551234`, so a prefix rule must not beat that contact. Task 3.
- Monday–Friday 22:00–07:00 includes Saturday 03:00 and excludes Monday 03:00. Task 2.
- An answer-and-hangup handoff for telecom id A must not answer telecom id B, even when the numbers match. Task 8.
- Before setup is complete, a matching block rule still allows the call. Task 6.

## File structure

```
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.properties
.gitignore
engine/build.gradle.kts
engine/src/main/kotlin/app/elbowsup/blocker/engine/TimeWindow.kt
engine/src/main/kotlin/app/elbowsup/blocker/engine/NumberNormalizer.kt
engine/src/main/kotlin/app/elbowsup/blocker/engine/Rule.kt
engine/src/main/kotlin/app/elbowsup/blocker/engine/Matcher.kt
engine/src/main/kotlin/app/elbowsup/blocker/engine/Pause.kt
engine/src/main/kotlin/app/elbowsup/blocker/engine/RuleEngine.kt
engine/src/main/kotlin/app/elbowsup/blocker/engine/RuleList.kt
engine/src/main/kotlin/app/elbowsup/blocker/engine/HandoffStore.kt
engine/src/main/kotlin/app/elbowsup/blocker/engine/ScreeningFlags.kt
engine/src/test/kotlin/app/elbowsup/blocker/engine/*Test.kt
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/kotlin/app/elbowsup/blocker/ElbowsUpApp.kt
app/src/main/kotlin/app/elbowsup/blocker/data/AppDatabase.kt
app/src/main/kotlin/app/elbowsup/blocker/data/Mappers.kt
app/src/main/kotlin/app/elbowsup/blocker/telecom/CallSnapshotFactory.kt
app/src/main/kotlin/app/elbowsup/blocker/telecom/ElbowsUpScreeningService.kt
app/src/main/kotlin/app/elbowsup/blocker/telecom/ElbowsUpInCallService.kt
app/src/main/kotlin/app/elbowsup/blocker/contacts/ContactDirectory.kt
app/src/main/kotlin/app/elbowsup/blocker/ui/MainActivity.kt
app/src/main/kotlin/app/elbowsup/blocker/ui/InCallActivity.kt
app/src/test/kotlin/app/elbowsup/blocker/data/MappersTest.kt
app/src/test/kotlin/app/elbowsup/blocker/contacts/ContactDirectoryTest.kt
```

The engine has no Android imports. The app adapts Telecom and Compose to engine types.

---

### Task 1: Gradle scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `engine/build.gradle.kts`, `.gitignore`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/ScaffoldTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `./gradlew :engine:test` runs JUnit 4 tests in the `engine` module

- [ ] **Step 1: Write the failing test**

```kotlin
package app.elbowsup.blocker.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaffoldTest {
    @Test
    fun engineModuleRunsUnitTests() {
        assertEquals(29, 29)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.ScaffoldTest`
Expected: FAIL because the Gradle project does not exist yet. If `./gradlew` is missing, generate it first in Step 3, then run this command and expect the test to pass only after the test file is on the source path. Do not skip the failure check if the project already exists: delete `engine/build.gradle.kts` temporarily only if the project was already created, then restore it in Step 3.

- [ ] **Step 3: Write the project files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "elbowsup"
include(":engine")
include(":app")
```

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.10"
junit = "4.13.2"
libphonenumber = "9.0.40"
composeBom = "2026.09.00"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
room = "2.8.5"
coreKtx = "1.17.0"
ksp = "2.3.12"

[libraries]
junit = { group = "junit", name = "junit", version.ref = "junit" }
libphonenumber = { group = "com.googlecode.libphonenumber", name = "libphonenumber", version.ref = "libphonenumber" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

`engine/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.libphonenumber)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
```

`.gitignore`:

```
.gradle/
build/
local.properties
*.iml
.idea/
.DS_Store
```

Create the wrapper:

```bash
gradle wrapper --gradle-version 9.6.0
```

`app` is included but has no build file yet. Comment `include(":app")` out until Task 9, or Step 3 of this task will fail configuration. Keep `include(":app")` commented:

```kotlin
// include(":app")
```

Task 9 removes the comment.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.ScaffoldTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle engine .gitignore gradlew gradlew.bat
git commit -m "chore: scaffold engine module for unit tests"
```

---

### Task 2: Time windows

**Files:**
- Create: `engine/src/main/kotlin/app/elbowsup/blocker/engine/TimeWindow.kt`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/TimeWindowTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `enum class DayOfWeek`, `data class TimeWindow(val days: Set<DayOfWeek>, val startMinute: Int, val endMinute: Int)`, `fun TimeWindow.contains(day: DayOfWeek, minuteOfDay: Int): Boolean`, `val DEFAULT_SLEEP`, `val DEFAULT_WORK`

Start minute is inclusive. End minute is exclusive. If `endMinute <= startMinute`, the window crosses midnight and the selected start day owns the spill into the next morning.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.elbowsup.blocker.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWindowTest {
    private val sleep = TimeWindow(
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        startMinute = 22 * 60,
        endMinute = 7 * 60,
    )

    @Test
    fun fridayNightSpillsIntoSaturdayMorning() {
        assertTrue(sleep.contains(DayOfWeek.FRIDAY, 23 * 60))
        assertTrue(sleep.contains(DayOfWeek.SATURDAY, 3 * 60))
        assertFalse(sleep.contains(DayOfWeek.MONDAY, 3 * 60))
    }

    @Test
    fun sameDayWindowIsStartInclusiveAndEndExclusive() {
        val work = DEFAULT_WORK
        assertTrue(work.contains(DayOfWeek.MONDAY, 9 * 60))
        assertFalse(work.contains(DayOfWeek.MONDAY, 17 * 60))
        assertFalse(work.contains(DayOfWeek.SATURDAY, 10 * 60))
    }

    @Test
    fun defaultSleepIsEveryDayFromTenToSeven() {
        assertTrue(DEFAULT_SLEEP.contains(DayOfWeek.SUNDAY, 22 * 60))
        assertTrue(DEFAULT_SLEEP.contains(DayOfWeek.MONDAY, 6 * 60))
        assertFalse(DEFAULT_SLEEP.contains(DayOfWeek.MONDAY, 7 * 60))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.TimeWindowTest`
Expected: FAIL, `TimeWindow` unresolved

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.elbowsup.blocker.engine

enum class DayOfWeek { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

data class TimeWindow(
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
)

fun TimeWindow.contains(day: DayOfWeek, minuteOfDay: Int): Boolean {
    if (endMinute > startMinute) {
        return day in days && minuteOfDay >= startMinute && minuteOfDay < endMinute
    }
    val previous = DayOfWeek.entries[(day.ordinal + 6) % 7]
    val evening = day in days && minuteOfDay >= startMinute
    val morningSpill = previous in days && minuteOfDay < endMinute
    return evening || morningSpill
}

val DEFAULT_SLEEP = TimeWindow(
    days = DayOfWeek.entries.toSet(),
    startMinute = 22 * 60,
    endMinute = 7 * 60,
)

val DEFAULT_WORK = TimeWindow(
    days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    startMinute = 9 * 60,
    endMinute = 17 * 60,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.TimeWindowTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add engine/src/main/kotlin/app/elbowsup/blocker/engine/TimeWindow.kt engine/src/test/kotlin/app/elbowsup/blocker/engine/TimeWindowTest.kt
git commit -m "feat: match overnight windows from the start day"
```

---

### Task 3: Number normalizer and chips

**Files:**
- Create: `engine/src/main/kotlin/app/elbowsup/blocker/engine/NumberNormalizer.kt`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/NumberNormalizerTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `data class ChipSet(val exact: String?, val areaCode: String?, val areaPlusPrefix: String?, val countryCode: String?)`
  - `class NumberNormalizer`
  - `fun resolveRegion(override: String?, simCountry: String?, localeCountry: String?): String?`
  - `fun storedForm(raw: String?, region: String?): String?`
  - `fun chips(stored: String?): ChipSet`
  - `fun sameStoredNumber(left: String?, right: String?): Boolean`

A settings country override wins over the SIM country. SIM wins over the locale. If all three are blank, `resolveRegion` returns null. `storedForm` returns null when `raw` has no digits. Prefer E.164. If E.164 cannot be produced, store digits and keep a leading `+` when the raw handle had one.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.elbowsup.blocker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberNormalizerTest {
    private val normalizer = NumberNormalizer()

    @Test
    fun countryOverrideBeatsSim() {
        assertEquals("GB", normalizer.resolveRegion("GB", "US", "CA"))
        assertEquals("US", normalizer.resolveRegion(null, "US", "CA"))
        assertEquals("CA", normalizer.resolveRegion("  ", null, "CA"))
        assertNull(normalizer.resolveRegion(null, null, null))
    }

    @Test
    fun nationalContactEqualsE164() {
        val fromContact = normalizer.storedForm("(415) 555-1234", "US")
        val fromNetwork = normalizer.storedForm("+1 415-555-1234", "US")
        assertEquals("+14155551234", fromContact)
        assertEquals(fromContact, fromNetwork)
        assertTrue(sameStoredNumber(fromContact, fromNetwork))
    }

    @Test
    fun nanpChipsUseAreaCodeAndPrefix() {
        val stored = normalizer.storedForm("+14155551234", "US")
        val chips = normalizer.chips(stored)
        assertEquals("+14155551234", chips.exact)
        assertEquals("+1415", chips.areaCode)
        assertEquals("+1415555", chips.areaPlusPrefix)
        assertNull(chips.countryCode)
    }

    @Test
    fun ukNumberGetsCountryChipOnly() {
        val stored = normalizer.storedForm("+442079460958", "US")
        val chips = normalizer.chips(stored)
        assertEquals("+442079460958", chips.exact)
        assertNull(chips.areaCode)
        assertNull(chips.areaPlusPrefix)
        assertEquals("+44", chips.countryCode)
    }

    @Test
    fun unparsableDigitsHaveExactChipOnly() {
        val stored = normalizer.storedForm("12345", null)
        assertEquals("12345", stored)
        val chips = normalizer.chips(stored)
        assertEquals("12345", chips.exact)
        assertNull(chips.areaCode)
        assertNull(chips.countryCode)
    }

    @Test
    fun noDigitsIsAbsent() {
        assertNull(normalizer.storedForm("No Caller ID", "US"))
        assertNull(normalizer.chips(null).exact)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.NumberNormalizerTest`
Expected: FAIL, `NumberNormalizer` unresolved

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.elbowsup.blocker.engine

import com.google.i18n.phonenumbers.PhoneNumberUtil

data class ChipSet(
    val exact: String?,
    val areaCode: String?,
    val areaPlusPrefix: String?,
    val countryCode: String?,
)

class NumberNormalizer(
    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
) {
    fun resolveRegion(override: String?, simCountry: String?, localeCountry: String?): String? {
        return listOf(override, simCountry, localeCountry)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?.uppercase()
    }

    fun storedForm(raw: String?, region: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val hadPlus = raw.trim().startsWith("+")
        if (region != null) {
            val parsed = runCatching { util.parse(raw, region) }.getOrNull()
            if (parsed != null && util.isValidNumber(parsed)) {
                return util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            }
        }
        return if (hadPlus) "+$digits" else digits
    }

    fun chips(stored: String?): ChipSet {
        if (stored == null) return ChipSet(null, null, null, null)
        val parsed = runCatching { util.parse(stored, null) }.getOrNull()
        if (parsed == null || !util.isValidNumber(parsed)) {
            return ChipSet(stored, null, null, null)
        }
        val e164 = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        if (parsed.countryCode == 1 && parsed.nationalNumber.toString().length == 10) {
            val national = parsed.nationalNumber.toString()
            return ChipSet(
                exact = e164,
                areaCode = "+1${national.take(3)}",
                areaPlusPrefix = "+1${national.take(6)}",
                countryCode = null,
            )
        }
        return ChipSet(e164, null, null, "+${parsed.countryCode}")
    }
}

fun sameStoredNumber(left: String?, right: String?): Boolean = left != null && left == right
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.NumberNormalizerTest`
Expected: PASS. If `12345` is considered a valid number in some region, change the unparsable fixture to `"**12-34**"` with region null and expect stored `"1234"` with only an exact chip. Do not weaken the NANP or UK assertions.

- [ ] **Step 5: Commit**

```bash
git add engine/src/main/kotlin/app/elbowsup/blocker/engine/NumberNormalizer.kt engine/src/test/kotlin/app/elbowsup/blocker/engine/NumberNormalizerTest.kt
git commit -m "feat: normalize numbers and build history chips"
```

---

### Task 4: Matchers

**Files:**
- Create: `engine/src/main/kotlin/app/elbowsup/blocker/engine/Rule.kt`
- Create: `engine/src/main/kotlin/app/elbowsup/blocker/engine/Matcher.kt`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/MatcherTest.kt`

**Interfaces:**
- Consumes: nothing from Task 3; matchers compare already-stored strings
- Produces:
  - `enum class MatcherType { EXACT, PREFIX, NUMBER_REGEX, NAME_WILDCARD, NAME_REGEX, EMPTY_NAME, NO_NUMBER }`
  - `enum class RuleKind { ALLOW, BLOCK }`
  - `enum class ScheduleKind { ALWAYS, SLEEP, WORK, CUSTOM }`
  - `enum class BlockAction { REJECT, ANSWER_HANGUP }`
  - `data class Rule(...)` as written below
  - `enum class MatchOutcome { MATCH, NO_MATCH, SKIP }`
  - `fun interface RegexRunner`
  - `class PatternTimeout : RuntimeException()`
  - `class BudgetRegexRunner(budgetMillis: Long = 50)`
  - `fun matchRule(rule: Rule, number: String?, displayName: String?, runner: RegexRunner): MatchOutcome`
  - `fun validateRegex(pattern: String): Boolean`
  - `fun ruleSummary(rule: Rule): String`

A blank display name is absent. Name wildcard and name regex do not match an absent name, including pattern `*`. Number regex uses `Regex.matches` on the whole stored number. A thrown or timed-out pattern returns `SKIP`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.elbowsup.blocker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherTest {
    private val runner = BudgetRegexRunner()

    private fun rule(
        matcher: MatcherType,
        pattern: String? = null,
        emptyNameOnly: Boolean = false,
        kind: RuleKind = RuleKind.BLOCK,
        action: BlockAction? = BlockAction.REJECT,
    ) = Rule(
        id = 1,
        position = 0,
        enabled = true,
        kind = kind,
        matcher = matcher,
        pattern = pattern,
        emptyNameOnly = emptyNameOnly,
        schedule = ScheduleKind.ALWAYS,
        customWindow = null,
        action = action,
    )

    @Test
    fun blankNameIsEmptyAndWildcardDoesNotMatchIt() {
        assertEquals(MatchOutcome.MATCH, matchRule(rule(MatcherType.EMPTY_NAME), "+14155551234", "   ", runner))
        assertEquals(MatchOutcome.NO_MATCH, matchRule(rule(MatcherType.NAME_WILDCARD, "*"), "+14155551234", "   ", runner))
        assertEquals(MatchOutcome.NO_MATCH, matchRule(rule(MatcherType.NAME_REGEX, "^$"), "+14155551234", null, runner))
        assertEquals(MatchOutcome.NO_MATCH, matchRule(rule(MatcherType.EMPTY_NAME), null, null, runner))
    }

    @Test
    fun unknownTextIsNotAnEmptyName() {
        assertEquals(MatchOutcome.NO_MATCH, matchRule(rule(MatcherType.EMPTY_NAME), "+14155551234", "Unknown", runner))
        assertEquals(MatchOutcome.MATCH, matchRule(rule(MatcherType.NAME_WILDCARD, "unk*"), "+14155551234", "Unknown", runner))
    }

    @Test
    fun numberRegexMustMatchTheWholeStoredNumber() {
        assertEquals(MatchOutcome.MATCH, matchRule(rule(MatcherType.NUMBER_REGEX, "\\+1415\\d{7}"), "+14155551234", "Ann", runner))
        assertEquals(MatchOutcome.NO_MATCH, matchRule(rule(MatcherType.NUMBER_REGEX, "415"), "+14155551234", "Ann", runner))
        assertEquals(MatchOutcome.NO_MATCH, matchRule(rule(MatcherType.EXACT, "+14155551234"), null, null, runner))
    }

    @Test
    fun emptyNameFlagRestrictsNumberRules() {
        val prefix = rule(MatcherType.PREFIX, "+1415", emptyNameOnly = true)
        assertEquals(MatchOutcome.MATCH, matchRule(prefix, "+14155551234", null, runner))
        assertEquals(MatchOutcome.NO_MATCH, matchRule(prefix, "+14155551234", "School", runner))
    }

    @Test
    fun timeoutSkipsTheRule() {
        val exploding = object : RegexRunner {
            override fun matches(pattern: String, input: String, caseInsensitive: Boolean): Boolean {
                throw PatternTimeout()
            }
        }
        assertEquals(MatchOutcome.SKIP, matchRule(rule(MatcherType.NUMBER_REGEX, ".*"), "+1", "Ann", exploding))
    }

    @Test
    fun invalidRegexCannotBeSaved() {
        assertFalse(validateRegex("("))
        assertTrue(validateRegex("\\+1\\d+"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.MatcherTest`
Expected: FAIL, `Rule` unresolved

- [ ] **Step 3: Write minimal implementation**

`Rule.kt`:

```kotlin
package app.elbowsup.blocker.engine

enum class MatcherType { EXACT, PREFIX, NUMBER_REGEX, NAME_WILDCARD, NAME_REGEX, EMPTY_NAME, NO_NUMBER }
enum class RuleKind { ALLOW, BLOCK }
enum class ScheduleKind { ALWAYS, SLEEP, WORK, CUSTOM }
enum class BlockAction { REJECT, ANSWER_HANGUP }

data class Rule(
    val id: Long,
    val position: Int,
    val enabled: Boolean,
    val kind: RuleKind,
    val matcher: MatcherType,
    val pattern: String?,
    val emptyNameOnly: Boolean,
    val schedule: ScheduleKind,
    val customWindow: TimeWindow?,
    val action: BlockAction?,
)
```

`Matcher.kt`:

```kotlin
package app.elbowsup.blocker.engine

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

enum class MatchOutcome { MATCH, NO_MATCH, SKIP }

fun interface RegexRunner {
    fun matches(pattern: String, input: String, caseInsensitive: Boolean): Boolean
}

class PatternTimeout : RuntimeException()

class BudgetRegexRunner(private val budgetMillis: Long = 50) : RegexRunner {
    private val executor = Executors.newCachedThreadPool()

    override fun matches(pattern: String, input: String, caseInsensitive: Boolean): Boolean {
        val future = executor.submit<Boolean> {
            val options = if (caseInsensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()
            Regex(pattern, options).matches(input)
        }
        return try {
            future.get(budgetMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw PatternTimeout()
        }
    }
}

fun validateRegex(pattern: String): Boolean = runCatching { Regex(pattern) }.isSuccess

fun matchRule(rule: Rule, number: String?, displayName: String?, runner: RegexRunner): MatchOutcome {
    val nameAbsent = displayName.isNullOrBlank()
    val numberMatch = when (rule.matcher) {
        MatcherType.EXACT -> if (number != null && number == rule.pattern) MatchOutcome.MATCH else MatchOutcome.NO_MATCH
        MatcherType.PREFIX -> if (number != null && rule.pattern != null && number.startsWith(rule.pattern)) MatchOutcome.MATCH else MatchOutcome.NO_MATCH
        MatcherType.NUMBER_REGEX -> regexAgainst(rule.pattern, number, runner, caseInsensitive = false)
        MatcherType.NAME_WILDCARD -> if (nameAbsent) MatchOutcome.NO_MATCH else wildcardAgainst(rule.pattern, displayName)
        MatcherType.NAME_REGEX -> if (nameAbsent) MatchOutcome.NO_MATCH else regexAgainst(rule.pattern, displayName, runner, caseInsensitive = false)
        MatcherType.EMPTY_NAME -> if (number != null && nameAbsent) MatchOutcome.MATCH else MatchOutcome.NO_MATCH
        MatcherType.NO_NUMBER -> if (number == null) MatchOutcome.MATCH else MatchOutcome.NO_MATCH
    }
    if (numberMatch != MatchOutcome.MATCH) return numberMatch
    if (rule.emptyNameOnly && rule.matcher in setOf(MatcherType.EXACT, MatcherType.PREFIX, MatcherType.NUMBER_REGEX) && !nameAbsent) {
        return MatchOutcome.NO_MATCH
    }
    return MatchOutcome.MATCH
}

private fun regexAgainst(pattern: String?, input: String?, runner: RegexRunner, caseInsensitive: Boolean): MatchOutcome {
    if (pattern == null || input == null) return MatchOutcome.NO_MATCH
    return try {
        if (runner.matches(pattern, input, caseInsensitive)) MatchOutcome.MATCH else MatchOutcome.NO_MATCH
    } catch (e: PatternTimeout) {
        MatchOutcome.SKIP
    } catch (e: RuntimeException) {
        MatchOutcome.SKIP
    }
}

private fun wildcardAgainst(pattern: String?, name: String?): MatchOutcome {
    if (pattern == null || name.isNullOrBlank()) return MatchOutcome.NO_MATCH
    val body = buildString {
        for (ch in pattern) {
            when (ch) {
                '*' -> append(".*")
                '?' -> append('.')
                else -> append(Regex.escape(ch.toString()))
            }
        }
    }
    val matched = runCatching { Regex("^$body$", RegexOption.IGNORE_CASE).matches(name) }.getOrDefault(false)
    return if (matched) MatchOutcome.MATCH else MatchOutcome.NO_MATCH
}

fun ruleSummary(rule: Rule): String {
    val kind = if (rule.kind == RuleKind.ALLOW) "Allow" else "Block"
    val what = when (rule.matcher) {
        MatcherType.EXACT -> "exact ${rule.pattern}"
        MatcherType.PREFIX -> "prefix ${rule.pattern}"
        MatcherType.NUMBER_REGEX -> "number regex ${rule.pattern}"
        MatcherType.NAME_WILDCARD -> "name ${rule.pattern}"
        MatcherType.NAME_REGEX -> "name regex ${rule.pattern}"
        MatcherType.EMPTY_NAME -> "empty name"
        MatcherType.NO_NUMBER -> "no number"
    }
    val flag = if (rule.emptyNameOnly) ", empty name only" else ""
    val action = if (rule.kind == RuleKind.BLOCK) {
        if (rule.action == BlockAction.ANSWER_HANGUP) ", answer and hang up" else ", reject"
    } else ""
    return "$kind $what$flag$action"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.MatcherTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add engine/src/main/kotlin/app/elbowsup/blocker/engine/Rule.kt engine/src/main/kotlin/app/elbowsup/blocker/engine/Matcher.kt engine/src/test/kotlin/app/elbowsup/blocker/engine/MatcherTest.kt
git commit -m "feat: match numbers, names, and empty caller names"
```

---

### Task 5: Pause and status line

**Files:**
- Create: `engine/src/main/kotlin/app/elbowsup/blocker/engine/Pause.kt`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/PauseTest.kt`

**Interfaces:**
- Consumes: `TimeWindow`, `DayOfWeek`, `TimeWindow.contains`
- Produces:
  - `data class PauseClock(val timedUntilEpochMillis: Long?, val untilResume: Boolean, val recurringEnabled: Boolean, val recurring: TimeWindow)`
  - `fun isPaused(pause: PauseClock, nowEpochMillis: Long, day: DayOfWeek, minuteOfDay: Int): Boolean`
  - `enum class BlockerStatus`
  - `fun blockerStatus(setupComplete: Boolean, permissionGranted: Boolean, visibleContactCount: Int, emptyBookOverride: Boolean, screeningHeld: Boolean, dialerHeld: Boolean, pause: PauseClock, nowEpochMillis: Long, day: DayOfWeek, minuteOfDay: Int): BlockerStatus`
  - `fun contactsBlockBlocking(permissionGranted: Boolean, visibleContactCount: Int, emptyBookOverride: Boolean): Boolean`

If `untilResume` is true, ignore `timedUntilEpochMillis`. Before setup, status is `SETUP_INCOMPLETE` even if other problems exist.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.elbowsup.blocker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseTest {
    private val window = TimeWindow(
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        startMinute = 9 * 60,
        endMinute = 17 * 60,
    )

    @Test
    fun timedPauseEndsAndRecurringKeepsBlockingOff() {
        val pause = PauseClock(1_000L, false, true, window)
        assertTrue(isPaused(pause, 999L, DayOfWeek.SATURDAY, 12 * 60))
        assertTrue(isPaused(pause, 1_000L, DayOfWeek.MONDAY, 10 * 60))
        assertFalse(isPaused(pause, 1_000L, DayOfWeek.SATURDAY, 12 * 60))
    }

    @Test
    fun untilResumeIgnoresATimestamp() {
        val pause = PauseClock(1_000L, true, false, window)
        assertEquals(
            BlockerStatus.PAUSED_UNTIL_RESUME,
            blockerStatus(true, true, 2, false, true, true, pause, 5_000L, DayOfWeek.SUNDAY, 0),
        )
    }

    @Test
    fun statusPriorityMatchesTheSpec() {
        val idle = PauseClock(null, false, false, window)
        assertEquals(BlockerStatus.SETUP_INCOMPLETE, blockerStatus(false, false, 0, false, false, false, idle, 0, DayOfWeek.MONDAY, 0))
        assertEquals(BlockerStatus.CONTACTS_UNREADABLE, blockerStatus(true, true, 0, false, true, true, idle, 0, DayOfWeek.MONDAY, 0))
        assertEquals(BlockerStatus.SCREENING_MISSING, blockerStatus(true, true, 3, false, false, false, idle, 0, DayOfWeek.MONDAY, 0))
        assertEquals(BlockerStatus.DIALER_MISSING, blockerStatus(true, true, 3, false, true, false, idle, 0, DayOfWeek.MONDAY, 0))
    }

    @Test
    fun emptyBookOverrideStopsTheContactsPause() {
        assertTrue(contactsBlockBlocking(true, 0, false))
        assertFalse(contactsBlockBlocking(true, 0, true))
        assertTrue(contactsBlockBlocking(false, 5, true))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.PauseTest`
Expected: FAIL, `PauseClock` unresolved

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.elbowsup.blocker.engine

data class PauseClock(
    val timedUntilEpochMillis: Long?,
    val untilResume: Boolean,
    val recurringEnabled: Boolean,
    val recurring: TimeWindow,
)

fun isPaused(pause: PauseClock, nowEpochMillis: Long, day: DayOfWeek, minuteOfDay: Int): Boolean {
    if (pause.untilResume) return true
    if (pause.timedUntilEpochMillis != null && nowEpochMillis < pause.timedUntilEpochMillis) return true
    return pause.recurringEnabled && pause.recurring.contains(day, minuteOfDay)
}

enum class BlockerStatus {
    SETUP_INCOMPLETE,
    CONTACTS_UNREADABLE,
    SCREENING_MISSING,
    DIALER_MISSING,
    PAUSED_UNTIL,
    PAUSED_UNTIL_RESUME,
    PAUSED_DAILY,
    ACTIVE,
}

fun contactsBlockBlocking(permissionGranted: Boolean, visibleContactCount: Int, emptyBookOverride: Boolean): Boolean {
    if (!permissionGranted) return true
    return visibleContactCount == 0 && !emptyBookOverride
}

fun blockerStatus(
    setupComplete: Boolean,
    permissionGranted: Boolean,
    visibleContactCount: Int,
    emptyBookOverride: Boolean,
    screeningHeld: Boolean,
    dialerHeld: Boolean,
    pause: PauseClock,
    nowEpochMillis: Long,
    day: DayOfWeek,
    minuteOfDay: Int,
): BlockerStatus {
    if (!setupComplete) return BlockerStatus.SETUP_INCOMPLETE
    if (contactsBlockBlocking(permissionGranted, visibleContactCount, emptyBookOverride)) return BlockerStatus.CONTACTS_UNREADABLE
    if (!screeningHeld) return BlockerStatus.SCREENING_MISSING
    if (!dialerHeld) return BlockerStatus.DIALER_MISSING
    if (pause.untilResume) return BlockerStatus.PAUSED_UNTIL_RESUME
    if (pause.timedUntilEpochMillis != null && nowEpochMillis < pause.timedUntilEpochMillis) return BlockerStatus.PAUSED_UNTIL
    if (pause.recurringEnabled && pause.recurring.contains(day, minuteOfDay)) return BlockerStatus.PAUSED_DAILY
    return BlockerStatus.ACTIVE
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.PauseTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add engine/src/main/kotlin/app/elbowsup/blocker/engine/Pause.kt engine/src/test/kotlin/app/elbowsup/blocker/engine/PauseTest.kt
git commit -m "feat: pause the blocker and rank status copy"
```

---

### Task 6: Rule engine

**Files:**
- Create: `engine/src/main/kotlin/app/elbowsup/blocker/engine/RuleEngine.kt`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/RuleEngineTest.kt`

**Interfaces:**
- Consumes: `Rule`, `MatchOutcome`, `matchRule`, `ruleSummary`, `TimeWindow`, `ScheduleKind`, `BlockAction`, `RegexRunner`, `PatternTimeout`
- Produces:
  - `enum class DecisionKind { ALLOW, REJECT, ANSWER_HANGUP }`
  - `data class Decision(val kind: DecisionKind, val winningRuleId: Long?, val ruleSummary: String?, val recordedAction: BlockAction?)`
  - `data class EngineContext(...)` as written below
  - `fun evaluate(number: String?, displayName: String?, context: EngineContext): Decision`

`rules` may be unsorted. Evaluation sorts by `position` ascending. A null contact cache is passed as `contactsBlockBlocking = true`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.elbowsup.blocker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEngineTest {
    private val runner = BudgetRegexRunner()
    private val sleep = DEFAULT_SLEEP

    private fun block(id: Long, position: Int, matcher: MatcherType, pattern: String?, action: BlockAction = BlockAction.REJECT, schedule: ScheduleKind = ScheduleKind.ALWAYS) =
        Rule(id, position, true, RuleKind.BLOCK, matcher, pattern, false, schedule, null, action)

    private fun allow(id: Long, position: Int, pattern: String) =
        Rule(id, position, true, RuleKind.ALLOW, MatcherType.EXACT, pattern, false, ScheduleKind.ALWAYS, null, null)

    private fun context(
        rules: List<Rule>,
        setupComplete: Boolean = true,
        screeningHeld: Boolean = true,
        dialerHeld: Boolean = true,
        contactsBlockBlocking: Boolean = false,
        isEmergency: Boolean = false,
        isContact: Boolean = false,
        pauseActive: Boolean = false,
        day: DayOfWeek = DayOfWeek.MONDAY,
        minuteOfDay: Int = 15 * 60,
    ) = EngineContext(setupComplete, screeningHeld, dialerHeld, contactsBlockBlocking, isEmergency, isContact, pauseActive, sleep, DEFAULT_WORK, day, minuteOfDay, rules, runner)

    @Test
    fun setupIncompleteAllowsAMatchingBlock() {
        val decision = evaluate("+14155551234", "Spam", context(listOf(block(1, 0, MatcherType.PREFIX, "+1415")), setupComplete = false))
        assertEquals(DecisionKind.ALLOW, decision.kind)
        assertNull(decision.winningRuleId)
    }

    @Test
    fun contactAndEmergencyAllowBeforeTheList() {
        val rules = listOf(block(1, 0, MatcherType.PREFIX, "+1415"))
        assertEquals(DecisionKind.ALLOW, evaluate("+14155551234", null, context(rules, isContact = true)).kind)
        assertEquals(DecisionKind.ALLOW, evaluate("+14155551234", null, context(rules, isEmergency = true)).kind)
    }

    @Test
    fun firstMatchWinsEvenWhenALaterPrefixIsLonger() {
        val rules = listOf(
            block(1, 0, MatcherType.PREFIX, "+1", BlockAction.REJECT),
            block(2, 1, MatcherType.PREFIX, "+1415555", BlockAction.ANSWER_HANGUP),
        )
        val decision = evaluate("+14155551234", "A", context(rules))
        assertEquals(1L, decision.winningRuleId)
        assertEquals(DecisionKind.REJECT, decision.kind)
    }

    @Test
    fun allowAboveABlockRingsAndBlockAboveAnAllowBlocks() {
        val number = "+14155551234"
        val allowFirst = listOf(allow(1, 0, number), block(2, 1, MatcherType.PREFIX, "+1415"))
        val blockFirst = listOf(block(2, 0, MatcherType.PREFIX, "+1415"), allow(1, 1, number))
        assertEquals(DecisionKind.ALLOW, evaluate(number, null, context(allowFirst)).kind)
        assertEquals(DecisionKind.REJECT, evaluate(number, null, context(blockFirst)).kind)
    }

    @Test
    fun pauseSkipsASleepRuleDuringSleep() {
        val rules = listOf(block(1, 0, MatcherType.NO_NUMBER, null, schedule = ScheduleKind.SLEEP))
        val duringSleep = context(rules, pauseActive = true, day = DayOfWeek.MONDAY, minuteOfDay = 23 * 60)
        assertEquals(DecisionKind.ALLOW, evaluate(null, null, duringSleep).kind)
    }

    @Test
    fun dialerLossRecordsReject() {
        val rules = listOf(block(1, 0, MatcherType.NO_NUMBER, null, BlockAction.ANSWER_HANGUP))
        val decision = evaluate(null, null, context(rules, dialerHeld = false))
        assertEquals(DecisionKind.REJECT, decision.kind)
        assertEquals(BlockAction.REJECT, decision.recordedAction)
    }

    @Test
    fun skippedRegexFallsThroughToTheNextRule() {
        val exploding = object : RegexRunner {
            override fun matches(pattern: String, input: String, caseInsensitive: Boolean): Boolean = throw PatternTimeout()
        }
        val rules = listOf(
            block(1, 0, MatcherType.NUMBER_REGEX, ".*"),
            block(2, 1, MatcherType.PREFIX, "+1415"),
        )
        val decision = evaluate("+14155551234", "A", context(rules).copy(patternRunner = exploding))
        assertEquals(2L, decision.winningRuleId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.RuleEngineTest`
Expected: FAIL, `evaluate` unresolved

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.elbowsup.blocker.engine

enum class DecisionKind { ALLOW, REJECT, ANSWER_HANGUP }

data class Decision(
    val kind: DecisionKind,
    val winningRuleId: Long?,
    val ruleSummary: String?,
    val recordedAction: BlockAction?,
)

data class EngineContext(
    val setupComplete: Boolean,
    val screeningHeld: Boolean,
    val dialerHeld: Boolean,
    val contactsBlockBlocking: Boolean,
    val isEmergency: Boolean,
    val isContact: Boolean,
    val pauseActive: Boolean,
    val sleep: TimeWindow,
    val work: TimeWindow,
    val day: DayOfWeek,
    val minuteOfDay: Int,
    val rules: List<Rule>,
    val patternRunner: RegexRunner,
)

fun evaluate(number: String?, displayName: String?, context: EngineContext): Decision {
    if (!context.setupComplete || !context.screeningHeld || context.contactsBlockBlocking) {
        return allow()
    }
    if (context.isEmergency || context.isContact || context.pauseActive) return allow()
    for (rule in context.rules.sortedBy { it.position }) {
        if (!rule.enabled || !scheduleActive(rule, context)) continue
        when (matchRule(rule, number, displayName, context.patternRunner)) {
            MatchOutcome.NO_MATCH, MatchOutcome.SKIP -> continue
            MatchOutcome.MATCH -> return decisionFor(rule, context.dialerHeld)
        }
    }
    return allow()
}

private fun allow() = Decision(DecisionKind.ALLOW, null, null, null)

private fun scheduleActive(rule: Rule, context: EngineContext): Boolean = when (rule.schedule) {
    ScheduleKind.ALWAYS -> true
    ScheduleKind.SLEEP -> context.sleep.contains(context.day, context.minuteOfDay)
    ScheduleKind.WORK -> context.work.contains(context.day, context.minuteOfDay)
    ScheduleKind.CUSTOM -> rule.customWindow?.contains(context.day, context.minuteOfDay) == true
}

private fun decisionFor(rule: Rule, dialerHeld: Boolean): Decision {
    val summary = ruleSummary(rule)
    if (rule.kind == RuleKind.ALLOW) return Decision(DecisionKind.ALLOW, rule.id, summary, null)
    val requested = rule.action ?: BlockAction.REJECT
    val recorded = if (!dialerHeld && requested == BlockAction.ANSWER_HANGUP) BlockAction.REJECT else requested
    val kind = if (recorded == BlockAction.ANSWER_HANGUP) DecisionKind.ANSWER_HANGUP else DecisionKind.REJECT
    return Decision(kind, rule.id, summary, recorded)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.RuleEngineTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add engine/src/main/kotlin/app/elbowsup/blocker/engine/RuleEngine.kt engine/src/test/kotlin/app/elbowsup/blocker/engine/RuleEngineTest.kt
git commit -m "feat: decide calls from ordered rules"
```

---

### Task 7: Rule list edits

**Files:**
- Create: `engine/src/main/kotlin/app/elbowsup/blocker/engine/RuleList.kt`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/RuleListTest.kt`

**Interfaces:**
- Consumes: `Rule`, `TimeWindow`, `RuleKind`, `MatcherType`, `ScheduleKind`, `BlockAction`
- Produces: `fun upsert(rules: List<Rule>, incoming: Rule): List<Rule>`, `fun move(rules: List<Rule>, fromIndex: Int, toIndex: Int): List<Rule>`

Identity is kind, matcher, pattern, empty-name flag, schedule kind, and custom window. Positions are rewritten to `0..n-1` in list order. Top is position 0.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.elbowsup.blocker.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleListTest {
    private fun rule(id: Long, position: Int, kind: RuleKind, pattern: String, action: BlockAction? = BlockAction.REJECT) =
        Rule(id, position, true, kind, MatcherType.EXACT, pattern, false, ScheduleKind.ALWAYS, null, action)

    @Test
    fun allowInsertsAtTopAndBlockInsertsAtBottom() {
        val existing = listOf(rule(1, 0, RuleKind.BLOCK, "+1415"))
        val withAllow = upsert(existing, rule(2, 99, RuleKind.ALLOW, "+14155551234", null))
        assertEquals(listOf(2L, 1L), withAllow.map { it.id })
        assertEquals(listOf(0, 1), withAllow.map { it.position })
        val withBlock = upsert(existing, rule(3, 0, RuleKind.BLOCK, "+1999"))
        assertEquals(listOf(1L, 3L), withBlock.map { it.id })
    }

    @Test
    fun duplicateUpdatesInPlaceAndDoesNotMove() {
        val existing = listOf(
            rule(1, 0, RuleKind.ALLOW, "+14155551234", null),
            rule(2, 1, RuleKind.BLOCK, "+1415", BlockAction.REJECT),
        )
        val updated = upsert(existing, rule(99, 0, RuleKind.BLOCK, "+1415", BlockAction.ANSWER_HANGUP))
        assertEquals(listOf(1L, 2L), updated.map { it.id })
        assertEquals(BlockAction.ANSWER_HANGUP, updated[1].action)
        assertEquals(1, updated[1].position)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.RuleListTest`
Expected: FAIL, `upsert` unresolved

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.elbowsup.blocker.engine

fun upsert(rules: List<Rule>, incoming: Rule): List<Rule> {
    val index = rules.indexOfFirst { sameIdentity(it, incoming) }
    val next = if (index >= 0) {
        rules.mapIndexed { i, rule ->
            if (i == index) rule.copy(enabled = incoming.enabled, action = incoming.action) else rule
        }
    } else if (incoming.kind == RuleKind.ALLOW) {
        listOf(incoming) + rules
    } else {
        rules + incoming
    }
    return next.mapIndexed { i, rule -> rule.copy(position = i) }
}

fun move(rules: List<Rule>, fromIndex: Int, toIndex: Int): List<Rule> {
    if (fromIndex !in rules.indices || toIndex !in rules.indices) return rules
    val mutable = rules.toMutableList()
    val moved = mutable.removeAt(fromIndex)
    mutable.add(toIndex, moved)
    return mutable.mapIndexed { i, rule -> rule.copy(position = i) }
}

private fun sameIdentity(left: Rule, right: Rule): Boolean {
    return left.kind == right.kind &&
        left.matcher == right.matcher &&
        left.pattern == right.pattern &&
        left.emptyNameOnly == right.emptyNameOnly &&
        left.schedule == right.schedule &&
        left.customWindow == right.customWindow
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.RuleListTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add engine/src/main/kotlin/app/elbowsup/blocker/engine/RuleList.kt engine/src/test/kotlin/app/elbowsup/blocker/engine/RuleListTest.kt
git commit -m "feat: insert allow and block rules without duplicates"
```

---

### Task 8: Handoff and screening flags

**Files:**
- Create: `engine/src/main/kotlin/app/elbowsup/blocker/engine/HandoffStore.kt`
- Create: `engine/src/main/kotlin/app/elbowsup/blocker/engine/ScreeningFlags.kt`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/TelecomPolicyTest.kt`

**Interfaces:**
- Consumes: `DecisionKind`, `BlockAction`
- Produces:
  - `class HandoffStore`
  - `fun HandoffStore.put(callId: String, atEpochMillis: Long)`
  - `fun HandoffStore.claim(callId: String, nowEpochMillis: Long): Boolean`
  - `data class ScreeningFlags(val disallow: Boolean, val reject: Boolean, val silence: Boolean, val skipCallLog: Boolean, val skipNotification: Boolean)`
  - `fun screeningFlags(kind: DecisionKind): ScreeningFlags`

`claim` returns true only for the same id, younger than 10_000 ms, and not yet claimed. It must not remove a different id.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.elbowsup.blocker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelecomPolicyTest {
    @Test
    fun handoffForADoesNotAnswerB() {
        val store = HandoffStore()
        store.put("A", 0L)
        assertFalse(store.claim("B", 1_000L))
        assertTrue(store.claim("A", 1_000L))
        assertFalse(store.claim("A", 1_100L))
    }

    @Test
    fun handoffOlderThanTenSecondsIsNotClaimed() {
        val store = HandoffStore()
        store.put("A", 0L)
        assertFalse(store.claim("A", 10_000L))
    }

    @Test
    fun flagsMatchTheSpec() {
        assertEquals(ScreeningFlags(false, false, false, false, false), screeningFlags(DecisionKind.ALLOW))
        assertEquals(ScreeningFlags(true, true, false, false, true), screeningFlags(DecisionKind.REJECT))
        assertEquals(ScreeningFlags(false, false, true, false, false), screeningFlags(DecisionKind.ANSWER_HANGUP))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.TelecomPolicyTest`
Expected: FAIL, `HandoffStore` unresolved

- [ ] **Step 3: Write minimal implementation**

`HandoffStore.kt`:

```kotlin
package app.elbowsup.blocker.engine

class HandoffStore {
    private val pending = linkedMapOf<String, Long>()

    fun put(callId: String, atEpochMillis: Long) {
        pending[callId] = atEpochMillis
    }

    fun claim(callId: String, nowEpochMillis: Long): Boolean {
        val at = pending[callId] ?: return false
        pending.remove(callId)
        return nowEpochMillis - at < 10_000L
    }
}
```

`ScreeningFlags.kt`:

```kotlin
package app.elbowsup.blocker.engine

data class ScreeningFlags(
    val disallow: Boolean,
    val reject: Boolean,
    val silence: Boolean,
    val skipCallLog: Boolean,
    val skipNotification: Boolean,
)

fun screeningFlags(kind: DecisionKind): ScreeningFlags = when (kind) {
    DecisionKind.ALLOW -> ScreeningFlags(false, false, false, false, false)
    DecisionKind.REJECT -> ScreeningFlags(true, true, false, false, true)
    DecisionKind.ANSWER_HANGUP -> ScreeningFlags(false, false, true, false, false)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :engine:test --tests app.elbowsup.blocker.engine.TelecomPolicyTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add engine/src/main/kotlin/app/elbowsup/blocker/engine/HandoffStore.kt engine/src/main/kotlin/app/elbowsup/blocker/engine/ScreeningFlags.kt engine/src/test/kotlin/app/elbowsup/blocker/engine/TelecomPolicyTest.kt
git commit -m "feat: pin screening flags and call handoff identity"
```

---

### Task 9: Android app module and Room

**Files:**
- Modify: `settings.gradle.kts` (uncomment `include(":app")`)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/app/elbowsup/blocker/data/AppDatabase.kt`
- Create: `app/src/main/kotlin/app/elbowsup/blocker/data/Mappers.kt`
- Test: `app/src/test/kotlin/app/elbowsup/blocker/data/MappersTest.kt`

**Interfaces:**
- Consumes: engine `Rule`, `TimeWindow`, `DayOfWeek`, `PauseClock`, `BlockAction`, `MatcherType`, `RuleKind`, `ScheduleKind`
- Produces: `RuleEntity`, `BlockedEventEntity`, `SettingsEntity`, `fun RuleEntity.toRule(): Rule`, `fun Rule.toEntity(): RuleEntity`, `fun SettingsEntity.toPauseClock(): PauseClock`, `AppDatabase`

Do not add `INTERNET`. Set `allowBackup` false in the manifest in Task 10; this task only needs the database to compile. Add a placeholder manifest so the Android plugin configures:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 1: Write the failing test**

```kotlin
package app.elbowsup.blocker.data

import app.elbowsup.blocker.engine.BlockAction
import app.elbowsup.blocker.engine.DayOfWeek
import app.elbowsup.blocker.engine.MatcherType
import app.elbowsup.blocker.engine.RuleKind
import app.elbowsup.blocker.engine.ScheduleKind
import app.elbowsup.blocker.engine.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {
    @Test
    fun customWindowRoundTrips() {
        val window = TimeWindow(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 22 * 60, 7 * 60)
        val rule = app.elbowsup.blocker.engine.Rule(
            id = 4,
            position = 1,
            enabled = true,
            kind = RuleKind.BLOCK,
            matcher = MatcherType.PREFIX,
            pattern = "+1415",
            emptyNameOnly = true,
            schedule = ScheduleKind.CUSTOM,
            customWindow = window,
            action = BlockAction.ANSWER_HANGUP,
        )
        val restored = rule.toEntity().toRule()
        assertEquals(window, restored.customWindow)
        assertEquals(BlockAction.ANSWER_HANGUP, restored.action)
        assertTrue(restored.emptyNameOnly)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests app.elbowsup.blocker.data.MappersTest`
Expected: FAIL, project or `toEntity` missing

- [ ] **Step 3: Write the app module and mappers**

Uncomment `include(":app")` in `settings.gradle.kts`.

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.elbowsup.blocker"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.elbowsup.blocker"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
}
```

`app/src/main/kotlin/app/elbowsup/blocker/data/Mappers.kt` and the entities in `AppDatabase.kt`:

```kotlin
package app.elbowsup.blocker.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import app.elbowsup.blocker.engine.BlockAction
import app.elbowsup.blocker.engine.DayOfWeek
import app.elbowsup.blocker.engine.MatcherType
import app.elbowsup.blocker.engine.PauseClock
import app.elbowsup.blocker.engine.Rule
import app.elbowsup.blocker.engine.RuleKind
import app.elbowsup.blocker.engine.ScheduleKind
import app.elbowsup.blocker.engine.TimeWindow

private fun Set<DayOfWeek>.encode(): String = joinToString(",") { it.name }
private fun String.decodeDays(): Set<DayOfWeek> =
    split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it) }.toSet()

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Int,
    val enabled: Boolean,
    val kind: String,
    val matcher: String,
    val pattern: String?,
    val emptyNameOnly: Boolean,
    val schedule: String,
    val customDays: String?,
    val customStart: Int?,
    val customEnd: Int?,
    val action: String?,
)

@Entity(tableName = "blocked_events")
data class BlockedEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeEpochMillis: Long,
    val number: String?,
    val displayName: String?,
    val action: String,
    val ruleId: Long?,
    val ruleSummary: String,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val sleepDays: String,
    val sleepStart: Int,
    val sleepEnd: Int,
    val workDays: String,
    val workStart: Int,
    val workEnd: Int,
    val recurringEnabled: Boolean,
    val recurringDays: String,
    val recurringStart: Int,
    val recurringEnd: Int,
    val timedUntilEpochMillis: Long?,
    val untilResume: Boolean,
    val countryOverride: String?,
    val emptyBookOverride: Boolean,
    val setupComplete: Boolean,
)

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    position = position,
    enabled = enabled,
    kind = kind.name,
    matcher = matcher.name,
    pattern = pattern,
    emptyNameOnly = emptyNameOnly,
    schedule = schedule.name,
    customDays = customWindow?.days?.encode(),
    customStart = customWindow?.startMinute,
    customEnd = customWindow?.endMinute,
    action = action?.name,
)

fun RuleEntity.toRule(): Rule = Rule(
    id = id,
    position = position,
    enabled = enabled,
    kind = RuleKind.valueOf(kind),
    matcher = MatcherType.valueOf(matcher),
    pattern = pattern,
    emptyNameOnly = emptyNameOnly,
    schedule = ScheduleKind.valueOf(schedule),
    customWindow = if (customDays != null && customStart != null && customEnd != null) {
        TimeWindow(customDays.decodeDays(), customStart, customEnd)
    } else {
        null
    },
    action = action?.let { BlockAction.valueOf(it) },
)

fun SettingsEntity.toPauseClock(): PauseClock = PauseClock(
    timedUntilEpochMillis = timedUntilEpochMillis,
    untilResume = untilResume,
    recurringEnabled = recurringEnabled,
    recurring = TimeWindow(recurringDays.decodeDays(), recurringStart, recurringEnd),
)

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY position ASC")
    suspend fun getRules(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity): Long

    @Query("DELETE FROM rules")
    suspend fun deleteAll()
}

@Dao
interface BlockedDao {
    @Insert
    suspend fun insert(event: BlockedEventEntity): Long

    @Query("SELECT * FROM blocked_events ORDER BY timeEpochMillis DESC")
    suspend fun all(): List<BlockedEventEntity>

    @Query("DELETE FROM blocked_events WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: SettingsEntity)
}

@Database(
    entities = [RuleEntity::class, BlockedEventEntity::class, SettingsEntity::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rules(): RuleDao
    abstract fun blocked(): BlockedDao
    abstract fun settings(): SettingsDao
}
```

Put the entities, DAOs, and `AppDatabase` in `AppDatabase.kt`. Put `toEntity`, `toRule`, and `toPauseClock` in `Mappers.kt`. The test imports `toEntity` and `toRule` from the data package, so those two functions must be in a file the test can see. Either file is fine if both are in `app.elbowsup.blocker.data`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests app.elbowsup.blocker.data.MappersTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts app
git commit -m "feat: persist rules and settings in Room"
```

The mapper implementation in Step 3 must be the full `Mappers.kt` and entity classes, not a sketch. Write every field listed in the spec's Stored data section. A missing custom-window field fails the test in Step 4.

---

### Task 10: Screening and in-call services

**Files:**
- Create: `app/src/main/AndroidManifest.xml` (replace the placeholder)
- Create: `app/src/main/kotlin/app/elbowsup/blocker/ElbowsUpApp.kt`
- Create: `app/src/main/kotlin/app/elbowsup/blocker/contacts/ContactDirectory.kt`
- Create: `app/src/main/kotlin/app/elbowsup/blocker/telecom/CallSnapshotFactory.kt`
- Create: `app/src/main/kotlin/app/elbowsup/blocker/telecom/ElbowsUpScreeningService.kt`
- Create: `app/src/main/kotlin/app/elbowsup/blocker/telecom/ElbowsUpInCallService.kt`
- Test: `app/src/test/kotlin/app/elbowsup/blocker/contacts/ContactDirectoryTest.kt`

**Interfaces:**
- Consumes: `evaluate`, `screeningFlags`, `HandoffStore`, `NumberNormalizer`, `contactsBlockBlocking`
- Produces: `ContactDirectory.isContact(stored: String?): Boolean`, `ContactDirectory.snapshot(): Set<String>?` (null until the first successful refresh), screening and in-call services

`ContactDirectoryTest` uses a fake loader, not the Android contacts provider:

```kotlin
@Test
fun nullCacheIsNotReady() {
    val directory = ContactDirectory { setOf("+14155551234") }
    assertEquals(null, directory.snapshot())
    directory.refresh()
    assertEquals(true, directory.isContact("+14155551234"))
    assertEquals(false, directory.isContact("+1999"))
}
```

Manifest requirements, all of them:

- `android:allowBackup="false"`
- No `INTERNET` permission
- Permissions: `READ_CONTACTS`, `CALL_PHONE`, `READ_CALL_LOG`, `WRITE_CALL_LOG`, `READ_PHONE_STATE`
- `ElbowsUpScreeningService` exported, permission `android.permission.BIND_SCREENING_SERVICE`, action `android.telecom.CallScreeningService`
- `ElbowsUpInCallService` exported, permission `android.permission.BIND_INCALL_SERVICE`, meta-data `android.telecom.IN_CALL_SERVICE_UI` and `android.telecom.IN_CALL_SERVICE_RINGING` true, action `android.telecom.InCallService`
- `MainActivity` handles `MAIN`/`LAUNCHER`, `ACTION_DIAL` with and without `tel`, `ACTION_VIEW` for `tel`, and `ACTION_DIAL` for `voicemail`
- `InCallActivity` has `showWhenLocked` and `turnScreenOn`

Screening service behavior:

1. Build the stored number with `NumberNormalizer` and the resolved region. A whitespace display name becomes null.
2. If `TelephonyManager.isEmergencyNumber` is true for the raw handle, respond allow and return.
3. If the rule cache or contact cache is null, respond allow and return. Do not query Room or contacts here.
4. Call `evaluate`. Map with `screeningFlags`.
5. Reject: insert the blocked event, then `respondToCall`. If `respondToCall` throws, delete that event.
6. Answer-and-hangup: if `callDetails.getId()` is null or blank, respond with allow flags (all false) and return. Otherwise `handoff.put(id, now)`, then respond with the answer-and-hangup flags. Do not write the blocked event yet.
7. Allow: respond with allow flags.

In-call service behavior:

1. On a ringing call, `claim(call.details.id, now)`.
2. If claimed, do not start `InCallActivity` and do not post a notification. `call.answer(VideoProfile.STATE_AUDIO_ONLY)`. When the call becomes active, `call.disconnect()`. Then insert the blocked event with action `ANSWER_HANGUP`. Leave the platform's call-log row in place.
3. If claim is false, start `InCallActivity` over the lock screen.
4. If disconnect throws, start `InCallActivity` so the user can hang up.
5. Never claim a call whose id was not stored. Do not match by number.

- [ ] **Step 1: Write the failing contact-directory test**

Use the test in the Interfaces section.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests app.elbowsup.blocker.contacts.ContactDirectoryTest`
Expected: FAIL, `ContactDirectory` unresolved

- [ ] **Step 3: Implement directory, manifest, and both services**

`ContactDirectory` holds `@Volatile var cache: Set<String>? = null`. `refresh()` sets `cache` to the loader result. `snapshot()` returns `cache`. `isContact` returns true only when `cache` contains the stored number.

`ElbowsUpApp` owns `NumberNormalizer`, `HandoffStore`, `ContactDirectory`, and an in-memory `EngineContext?` rule cache refreshed from Room on a background dispatcher whenever rules or settings change. The screening service reads that cache and does not open the database.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests app.elbowsup.blocker.contacts.ContactDirectoryTest`
Expected: PASS

Also run `./gradlew :app:assembleDebug` and confirm the merged manifest has no `INTERNET` permission.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/kotlin app/src/test/kotlin/app/elbowsup/blocker/contacts
git commit -m "feat: screen calls and answer-hangup by telecom id"
```

---

### Task 11: UI for setup, rules, history, blocked, keypad, and pause

**Files:**
- Create: `app/src/main/kotlin/app/elbowsup/blocker/ui/MainActivity.kt`
- Create: `app/src/main/kotlin/app/elbowsup/blocker/ui/InCallActivity.kt`
- Create: `app/src/main/kotlin/app/elbowsup/blocker/ui/BlockerStatusText.kt`
- Test: `app/src/test/kotlin/app/elbowsup/blocker/ui/BlockerStatusTextTest.kt`

**Interfaces:**
- Consumes: `blockerStatus`, `upsert`, `move`, `ChipSet`, `BlockerStatus`
- Produces: the four tabs and the in-call screen described in the spec

`blockerStatusText` maps each `BlockerStatus` to the spec's copy:

- `SETUP_INCOMPLETE` → "Blocking is off until setup is finished."
- `CONTACTS_UNREADABLE` → "Blocking is off because contacts are not readable."
- `SCREENING_MISSING` → "Blocking is off because ElbowsUp is not the screening app."
- `DIALER_MISSING` → "Answer and hang up is unavailable because ElbowsUp is not the phone app. Reject still runs."
- `PAUSED_UNTIL` → "Paused until {local time}."
- `PAUSED_UNTIL_RESUME` → "Paused until you resume."
- `PAUSED_DAILY` → "Paused for the daily window."
- `ACTIVE` → "Blocking on."

- [ ] **Step 1: Write the failing test**

```kotlin
package app.elbowsup.blocker.ui

import app.elbowsup.blocker.engine.BlockerStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockerStatusTextTest {
    @Test
    fun setupCopyDoesNotClaimRejectRuns() {
        assertEquals(
            "Blocking is off until setup is finished.",
            blockerStatusText(BlockerStatus.SETUP_INCOMPLETE, null),
        )
    }

    @Test
    fun dialerLossCopySaysRejectStillRuns() {
        assertEquals(
            "Answer and hang up is unavailable because ElbowsUp is not the phone app. Reject still runs.",
            blockerStatusText(BlockerStatus.DIALER_MISSING, null),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests app.elbowsup.blocker.ui.BlockerStatusTextTest`
Expected: FAIL, `blockerStatusText` unresolved

- [ ] **Step 3: Implement status text and the screens**

```kotlin
fun blockerStatusText(status: BlockerStatus, untilLabel: String?): String = when (status) {
    BlockerStatus.SETUP_INCOMPLETE -> "Blocking is off until setup is finished."
    BlockerStatus.CONTACTS_UNREADABLE -> "Blocking is off because contacts are not readable."
    BlockerStatus.SCREENING_MISSING -> "Blocking is off because ElbowsUp is not the screening app."
    BlockerStatus.DIALER_MISSING -> "Answer and hang up is unavailable because ElbowsUp is not the phone app. Reject still runs."
    BlockerStatus.PAUSED_UNTIL -> "Paused until ${untilLabel ?: "later"}."
    BlockerStatus.PAUSED_UNTIL_RESUME -> "Paused until you resume."
    BlockerStatus.PAUSED_DAILY -> "Paused for the daily window."
    BlockerStatus.ACTIVE -> "Blocking on."
}
```

`MainActivity` requests `ROLE_DIALER`, `ROLE_CALL_SCREENING`, and `READ_CONTACTS` before it sets the setup-completed flag. The app bar shows `blockerStatusText` and a Pause button. Pause offers 15 minutes, 1 hour, or until resume. Resume clears the timed pause. A timed pause posts an ongoing notification with a Resume action. The recurring window does not post a notification.

Tabs:

- Keypad places calls with `TelecomManager.placeCall`. A `tel:` intent fills the number.
- History reads `CallLog.Calls` (including blocked entries, labelled Blocked) and opens an add-rule sheet. Chips from a numbered row are Block this number, Allow this number, and the chips `NumberNormalizer.chips` returns (area code and area-plus-prefix, or country code). A blank display name also offers Block empty caller names, which creates an `EMPTY_NAME` block rule, not a number rule with the flag. A row with no number offers Block calls with no number. Block sheets prefill Reject and Always. Allow sheets have no action. Save calls `upsert`.
- Blocked lists ElbowsUp events. "Allow this number" upserts an allow rule. Open rule navigates to that rule when the id still exists.
- Rules shows the ordered list, drag-reorders with `move`, and adds regex, name, empty name, no number, and typed prefix rules. Save is refused when `validateRegex` is false.
- Settings edits Sleep, Work, default country, the one recurring pause window, and the empty-book override. Copy next to the override says hidden GrapheneOS contacts are not protected.

`InCallActivity` shows answer, decline, mute, speaker, keypad, hang up, and hold only when `call.details.can(Call.Details.CAPABILITY_HOLD)` is true. Answering a second call holds the first when hold is supported. It is the screen shown for allowed calls and for a missed answer-and-hangup handoff. It is not shown when a handoff is claimed.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests app.elbowsup.blocker.ui.BlockerStatusTextTest`
Expected: PASS

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/elbowsup/blocker/ui app/src/test/kotlin/app/elbowsup/blocker/ui
git commit -m "feat: add dialer screens, rule list, and pause"
```

---

### Task 12: Device checks

**Files:**
- Modify: none, unless a device check fails. Then fix the owning task's code and add a unit test that would have caught it.

**Interfaces:**
- Consumes: the installed debug APK
- Produces: a note in the commit message listing which checks were run on a device and which were not

- [ ] **Step 1: Install the debug build**

Run: `./gradlew :app:installDebug`
Expected: INSTALL SUCCESSFUL on a phone or emulator with API 29 or higher. GrapheneOS is the preferred device. If no device is attached, do not claim these checks passed. Record that in the commit message and stop this task.

- [ ] **Step 2: Run the spec's device checks**

- Reject does not ring and does not raise a missed-call notification. The row is in Blocked and in History; the system call log shows a blocked entry.
- Answer-and-hangup does not show the incoming screen and lands in Blocked and in History.
- A visible contact still rings when a prefix rule matches that number.
- An emergency call places and is not screened.
- Blocking stays off until dialer, screening, and contacts are all granted.
- Revoking contacts pauses blocking.
- Pause for 1 hour lets a matching block rule ring. Resume makes the next match block again.

- [ ] **Step 3: Commit any fixes**

```bash
git add -u
git commit -m "fix: correct device-check failures in call screening"
```

Skip the commit if no code changed. Do not commit a false claim that device checks passed.

---

## Spec coverage

- Roles, setup flag, and later dialer loss: Tasks 6, 10, 11
- Normalization, chips, country override: Task 3
- Matchers, empty name, regex budget: Task 4
- Ordered allow/block list and duplicates: Task 7
- Pause, status priority, midnight windows: Tasks 2 and 5
- Screening flags, handoff id: Tasks 8 and 10
- Room storage: Task 9
- Screens, keypad, in-call, lock screen: Task 11
- Device checks: Task 12
- LLM, forwarding, message playback, Quick Settings tile, import/export, per-SIM rules: not in this plan
