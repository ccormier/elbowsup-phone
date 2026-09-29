# ElbowsUp Follow-ups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the blocker status indicator, add system-settings shortcuts and answer-hangup gating, replace the Sleep/Work/recurring-pause presets with named schedules, and fold the Blocked screen into a History detail dialog.

**Architecture:** The `:engine` module replaces `SLEEP`/`WORK` schedule kinds with a `Schedule` value type (name + window + action, today only `PAUSE`) and drops the preset windows. The `:app` module persists schedules in a new Room table (`schedules`, DB v2 migration), renders schedule CRUD in Settings, and matches system call-log rows to blocked events so History can show action, rule summary, allow, and open-rule actions in a detail dialog.

**Tech Stack:** Kotlin 2.4.10, Jetpack Compose, Room 2.8.5, JUnit 4, Gradle 9.6.0, minSdk 29.

**Spec:** `docs/superpowers/specs/2026-09-26-elbowsup-call-blocker-design.md` — Task 7 updates it to match this plan; read both.

## Global Constraints

- No `INTERNET` permission, no network calls. `android:allowBackup="false"`.
- `:engine` stays pure Kotlin — no Android imports.
- Fail open: missing cache, engine throw, unreadable contacts, incomplete setup all allow the call.
- The call path is cache-only: `onScreenCall` reads in-memory rules, contacts, schedules, and pause; never Room.
- Emergency numbers, visible contacts, and pause allow before the rule list.
- Numbers compare in stored form (E.164 preferred) everywhere.
- Answer-and-hangup is keyed by `Call.Details.getId()`, never by phone number.
- A block is only ever the deliberate result of a matching rule.
- Reject flags: disallow + reject + skip notification, no silence. Answer-hangup flags: silence, no disallow.
- After setup, when ElbowsUp is not the default dialer, an answer-hangup rule records and performs reject.

## Review Focus

Inputs and failure modes most likely to bite a person using this software; each has a test in the owning task.

- A v1 database with SLEEP/WORK rules and an enabled recurring pause must migrate without losing the windows or the pause. Task 2.
- A disabled schedule must not pause, and a schedule window crossing midnight must pause the next morning. Task 1.
- A blocked event must match only the right call-log row (same stored number, compatible type, within two minutes) and never be reused by a second row. Task 6.
- With no dialer role, an answer-hangup rule must reject the call and record reject, including a call with no number. Task 1.
- A history row from another number between two blocked attempts must end the fold. Task 6.

---

### Task 1: Engine — schedules replace presets

**Files:**
- Create: `engine/src/main/kotlin/app/elbowsup/blocker/engine/Schedule.kt`
- Modify: `engine/src/main/kotlin/app/elbowsup/blocker/engine/Rule.kt`
- Modify: `engine/src/main/kotlin/app/elbowsup/blocker/engine/TimeWindow.kt`
- Modify: `engine/src/main/kotlin/app/elbowsup/blocker/engine/Pause.kt`
- Modify: `engine/src/main/kotlin/app/elbowsup/blocker/engine/RuleEngine.kt`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/PauseTest.kt`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/RuleEngineTest.kt`
- Test: `engine/src/test/kotlin/app/elbowsup/blocker/engine/TimeWindowTest.kt`

**Interfaces:**
- Produces: `ScheduleAction { PAUSE }`, `Schedule(id, name, enabled, window, action)`, `PauseClock(timedUntilEpochMillis, untilResume)`, `activeSchedule(schedules, day, minuteOfDay): Schedule?`, `isPaused(pause, schedules, nowEpochMillis, day, minuteOfDay): Boolean`, `blockerStatus(..., pause, schedules, nowEpochMillis, day, minuteOfDay)`, `BlockerStatus.PAUSED_SCHEDULE`, `ScheduleKind { ALWAYS, CUSTOM }`, `EngineContext` without `sleep`/`work`.

- [ ] **Step 1: Write the failing tests**

`Schedule.kt` does not exist yet, so `PauseTest` fails to compile. Rewrite `PauseTest.kt`:

```kotlin
package app.elbowsup.blocker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseTest {
    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val work = TimeWindow(weekdays, 9 * 60, 17 * 60)
    private val sleep = TimeWindow(DayOfWeek.entries.toSet(), 22 * 60, 7 * 60)

    private fun schedule(
        id: Long = 1,
        enabled: Boolean = true,
        window: TimeWindow = work,
    ) = Schedule(id, "Work", enabled, window, ScheduleAction.PAUSE)

    @Test
    fun activeSchedulePausesInsideItsWindowOnly() {
        val schedules = listOf(schedule())
        assertTrue(isPaused(PauseClock(null, false), schedules, 1_000L, DayOfWeek.MONDAY, 10 * 60))
        assertFalse(isPaused(PauseClock(null, false), schedules, 1_000L, DayOfWeek.MONDAY, 18 * 60))
        assertFalse(isPaused(PauseClock(null, false), schedules, 1_000L, DayOfWeek.SATURDAY, 10 * 60))
    }

    @Test
    fun disabledScheduleDoesNotPause() {
        val schedules = listOf(schedule(enabled = false))
        assertFalse(isPaused(PauseClock(null, false), schedules, 1_000L, DayOfWeek.MONDAY, 10 * 60))
        assertNull(activeSchedule(schedules, DayOfWeek.MONDAY, 10 * 60))
    }

    @Test
    fun aWindowCrossingMidnightPausesTheNextMorning() {
        val schedules = listOf(schedule(window = sleep))
        assertTrue(isPaused(PauseClock(null, false), schedules, 1_000L, DayOfWeek.FRIDAY, 23 * 60))
        assertTrue(isPaused(PauseClock(null, false), schedules, 1_000L, DayOfWeek.SATURDAY, 3 * 60))
        assertFalse(isPaused(PauseClock(null, false), schedules, 1_000L, DayOfWeek.MONDAY, 3 * 60))
    }

    @Test
    fun timedPauseEndsAndAScheduleStillPauses() {
        val schedules = listOf(schedule())
        assertTrue(isPaused(PauseClock(1_000L, false), schedules, 999L, DayOfWeek.SATURDAY, 12 * 60))
        assertTrue(isPaused(PauseClock(1_000L, false), schedules, 1_000L, DayOfWeek.MONDAY, 10 * 60))
        assertFalse(isPaused(PauseClock(1_000L, false), schedules, 1_000L, DayOfWeek.SATURDAY, 12 * 60))
    }

    @Test
    fun untilResumeIgnoresATimestamp() {
        val pause = PauseClock(1_000L, true)
        assertEquals(
            BlockerStatus.PAUSED_UNTIL_RESUME,
            blockerStatus(true, true, 2, false, true, true, pause, emptyList(), 5_000L, DayOfWeek.SUNDAY, 0),
        )
    }

    @Test
    fun anActiveScheduleReportsTheSchedulePauseStatus() {
        assertEquals(
            BlockerStatus.PAUSED_SCHEDULE,
            blockerStatus(true, true, 2, false, true, true, PauseClock(null, false), listOf(schedule()), 5_000L, DayOfWeek.MONDAY, 10 * 60),
        )
    }

    @Test
    fun statusPriorityMatchesTheSpec() {
        val idle = PauseClock(null, false)
        assertEquals(BlockerStatus.SETUP_INCOMPLETE, blockerStatus(false, false, 0, false, false, false, idle, emptyList(), 0, DayOfWeek.MONDAY, 0))
        assertEquals(BlockerStatus.CONTACTS_UNREADABLE, blockerStatus(true, true, 0, false, true, true, idle, emptyList(), 0, DayOfWeek.MONDAY, 0))
        assertEquals(BlockerStatus.SCREENING_MISSING, blockerStatus(true, true, 3, false, false, false, idle, emptyList(), 0, DayOfWeek.MONDAY, 0))
        assertEquals(BlockerStatus.DIALER_MISSING, blockerStatus(true, true, 3, false, true, false, idle, emptyList(), 0, DayOfWeek.MONDAY, 0))
        assertEquals(BlockerStatus.ACTIVE, blockerStatus(true, true, 3, false, true, true, idle, emptyList(), 0, DayOfWeek.MONDAY, 0))
    }

    @Test
    fun emptyBookOverrideStopsTheContactsPause() {
        assertTrue(contactsBlockBlocking(true, 0, false))
        assertFalse(contactsBlockBlocking(true, 0, true))
        assertTrue(contactsBlockBlocking(false, 5, true))
    }
}
```

Update `TimeWindowTest.kt`: drop the two preset tests, keep the crossing test with a local window:

```kotlin
    @Test
    fun fridayNightSpillsIntoSaturdayMorning() { ... existing ... }

    @Test
    fun sameDayWindowIsStartInclusiveAndEndExclusive() {
        val work = TimeWindow(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            9 * 60,
            17 * 60,
        )
        assertTrue(work.contains(DayOfWeek.MONDAY, 9 * 60))
        assertFalse(work.contains(DayOfWeek.MONDAY, 17 * 60))
        assertFalse(work.contains(DayOfWeek.SATURDAY, 10 * 60))
    }
```

Update `RuleEngineTest.kt`: `context()` loses `sleep`/`work` (use `EngineContext(..., pauseActive, day, minuteOfDay, rules, runner)`), and `pauseSkipsASleepRuleDuringSleep` becomes a custom-window test:

```kotlin
    @Test
    fun pauseSkipsARuleWhoseWindowIsActive() {
        val rule = block(1, 0, MatcherType.NO_NUMBER, null).copy(
            schedule = ScheduleKind.CUSTOM,
            customWindow = TimeWindow(setOf(DayOfWeek.MONDAY), 22 * 60, 7 * 60),
        )
        val decision = evaluate(null, null, context(listOf(rule), pauseActive = true, day = DayOfWeek.MONDAY, minuteOfDay = 23 * 60))
        assertEquals(DecisionKind.ALLOW, decision.kind)
    }
```

Add an engine fallback test for a no-number answer-hangup rule:

```kotlin
    @Test
    fun dialerLossRecordsRejectForANoNumberAnswerHangupRule() {
        val rules = listOf(block(1, 0, MatcherType.NO_NUMBER, null, BlockAction.ANSWER_HANGUP))
        val decision = evaluate(null, null, context(rules, dialerHeld = false))
        assertEquals(DecisionKind.REJECT, decision.kind)
        assertEquals(BlockAction.REJECT, decision.recordedAction)
    }
```

- [ ] **Step 2: Run the engine tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew :engine:test`
Expected: compilation failure — `Schedule`, `ScheduleAction`, `PAUSED_SCHEDULE`, and the new `PauseClock`/`blockerStatus`/`EngineContext` shapes do not exist.

- [ ] **Step 3: Write the engine implementation**

`Schedule.kt`:

```kotlin
package app.elbowsup.blocker.engine

enum class ScheduleAction { PAUSE }

data class Schedule(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val window: TimeWindow,
    val action: ScheduleAction,
)
```

`Rule.kt`: `enum class ScheduleKind { ALWAYS, CUSTOM }`.

`TimeWindow.kt`: delete `DEFAULT_SLEEP` and `DEFAULT_WORK`.

`Pause.kt`:

```kotlin
package app.elbowsup.blocker.engine

data class PauseClock(
    val timedUntilEpochMillis: Long?,
    val untilResume: Boolean,
)

fun activeSchedule(schedules: List<Schedule>, day: DayOfWeek, minuteOfDay: Int): Schedule? =
    schedules.firstOrNull {
        it.enabled && it.action == ScheduleAction.PAUSE && it.window.contains(day, minuteOfDay)
    }

fun isPaused(
    pause: PauseClock,
    schedules: List<Schedule>,
    nowEpochMillis: Long,
    day: DayOfWeek,
    minuteOfDay: Int,
): Boolean {
    if (pause.untilResume) return true
    if (pause.timedUntilEpochMillis != null && nowEpochMillis < pause.timedUntilEpochMillis) return true
    return activeSchedule(schedules, day, minuteOfDay) != null
}

enum class BlockerStatus {
    SETUP_INCOMPLETE,
    CONTACTS_UNREADABLE,
    SCREENING_MISSING,
    DIALER_MISSING,
    PAUSED_UNTIL,
    PAUSED_UNTIL_RESUME,
    PAUSED_SCHEDULE,
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
    schedules: List<Schedule>,
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
    if (activeSchedule(schedules, day, minuteOfDay) != null) return BlockerStatus.PAUSED_SCHEDULE
    return BlockerStatus.ACTIVE
}
```

`RuleEngine.kt`: `EngineContext` drops `sleep` and `work`; `scheduleActive` becomes:

```kotlin
private fun scheduleActive(rule: Rule, context: EngineContext): Boolean = when (rule.schedule) {
    ScheduleKind.ALWAYS -> true
    ScheduleKind.CUSTOM -> rule.customWindow?.contains(context.day, context.minuteOfDay) == true
}
```

- [ ] **Step 4: Run the engine tests to verify they pass**

Run: `./gradlew :engine:test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add engine/src
git commit -m "feat: replace sleep and work presets with named schedules in the engine"
```

---

### Task 2: App data — schedules table, slim settings, v1→v2 migration

**Files:**
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/data/AppDatabase.kt`
- Create: `app/src/main/kotlin/app/elbowsup/blocker/data/Migrations.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/data/Mappers.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ElbowsUpApp.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/AppStores.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/UiTime.kt`
- Test: `app/src/test/kotlin/app/elbowsup/blocker/data/MappersTest.kt`

**Interfaces:**
- Produces: `ScheduleEntity(id, name, enabled, days, start, end, action)`, `ScheduleDao.all()/upsert()/delete(id)`, `MIGRATION_1_2`, `Schedule.toEntity()`, `ScheduleEntity.toSchedule()`, `SettingsEntity.toPauseClock(): PauseClock`, `StoredSnapshot(settings, rules, blocked, schedules)`, `AppStores.saveSchedule/deleteSchedule`, `defaultSettings()` without preset fields.

- [ ] **Step 1: Write the failing test**

Add to `MappersTest.kt`:

```kotlin
    @Test
    fun scheduleRoundTrips() {
        val schedule = app.elbowsup.blocker.engine.Schedule(
            id = 9,
            name = "Work",
            enabled = false,
            window = TimeWindow(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 9 * 60, 17 * 60),
            action = app.elbowsup.blocker.engine.ScheduleAction.PAUSE,
        )
        val restored = schedule.toEntity().toSchedule()
        assertEquals(schedule, restored)
    }

    @Test
    fun settingsPauseClockKeepsTimedPauseOnly() {
        val settings = SettingsEntity(
            timedUntilEpochMillis = 5_000L,
            untilResume = true,
            countryOverride = "US",
            emptyBookOverride = false,
            setupComplete = true,
        )
        val pause = settings.toPauseClock()
        assertEquals(5_000L, pause.timedUntilEpochMillis)
        assertTrue(pause.untilResume)
    }
```

- [ ] **Step 2: Run the app tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew :app:testDebugUnitTest`
Expected: compilation failure — `ScheduleEntity`, `toSchedule`, and the slim `SettingsEntity` constructor do not exist.

- [ ] **Step 3: Write the data implementation**

`AppDatabase.kt`:

```kotlin
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val days: String,
    val start: Int,
    val end: Int,
    val action: String,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val timedUntilEpochMillis: Long?,
    val untilResume: Boolean,
    val countryOverride: String?,
    val emptyBookOverride: Boolean,
    val setupComplete: Boolean,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY id ASC")
    suspend fun all(): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ScheduleEntity): Long

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [RuleEntity::class, BlockedEventEntity::class, SettingsEntity::class, ScheduleEntity::class],
    version = 2,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rules(): RuleDao
    abstract fun blocked(): BlockedDao
    abstract fun settings(): SettingsDao
    abstract fun schedules(): ScheduleDao
}
```

`Migrations.kt` — preserve every v1 value. SLEEP/WORK rules become custom windows with the stored preset, the recurring pause becomes a `PAUSE` schedule, and the settings table is rebuilt without the preset columns:

```kotlin
package app.elbowsup.blocker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `days` TEXT NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `action` TEXT NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `schedules` (`name`, `enabled`, `days`, `start`, `end`, `action`) SELECT 'Daily pause', `recurringEnabled`, `recurringDays`, `recurringStart`, `recurringEnd`, 'PAUSE' FROM `settings` WHERE `id` = 1",
        )
        db.execSQL(
            "UPDATE `rules` SET `schedule` = 'CUSTOM', `customDays` = COALESCE((SELECT `sleepDays` FROM `settings` WHERE `id` = 1), 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY'), `customStart` = COALESCE((SELECT `sleepStart` FROM `settings` WHERE `id` = 1), 1320), `customEnd` = COALESCE((SELECT `sleepEnd` FROM `settings` WHERE `id` = 1), 420) WHERE `schedule` = 'SLEEP'",
        )
        db.execSQL(
            "UPDATE `rules` SET `schedule` = 'CUSTOM', `customDays` = COALESCE((SELECT `workDays` FROM `settings` WHERE `id` = 1), 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY'), `customStart` = COALESCE((SELECT `workStart` FROM `settings` WHERE `id` = 1), 540), `customEnd` = COALESCE((SELECT `workEnd` FROM `settings` WHERE `id` = 1), 1020) WHERE `schedule` = 'WORK'",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `settings_new` (`id` INTEGER NOT NULL, `timedUntilEpochMillis` INTEGER, `untilResume` INTEGER NOT NULL, `countryOverride` TEXT, `emptyBookOverride` INTEGER NOT NULL, `setupComplete` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT INTO `settings_new` (`id`, `timedUntilEpochMillis`, `untilResume`, `countryOverride`, `emptyBookOverride`, `setupComplete`) SELECT `id`, `timedUntilEpochMillis`, `untilResume`, `countryOverride`, `emptyBookOverride`, `setupComplete` FROM `settings`",
        )
        db.execSQL("DROP TABLE `settings`")
        db.execSQL("ALTER TABLE `settings_new` RENAME TO `settings`")
    }
}
```

`Mappers.kt`: add schedule mappers, slim `toPauseClock`, delete `sleepWindow`/`workWindow`:

```kotlin
fun Schedule.toEntity(): ScheduleEntity = ScheduleEntity(
    id = id,
    name = name,
    enabled = enabled,
    days = window.days.encode(),
    start = window.startMinute,
    end = window.endMinute,
    action = action.name,
)

fun ScheduleEntity.toSchedule(): Schedule = Schedule(
    id = id,
    name = name,
    enabled = enabled,
    window = TimeWindow(days.decodeDays(), start, end),
    action = ScheduleAction.valueOf(action),
)

fun SettingsEntity.toPauseClock(): PauseClock = PauseClock(
    timedUntilEpochMillis = timedUntilEpochMillis,
    untilResume = untilResume,
)
```

`ElbowsUpApp.kt`: `.addMigrations(MIGRATION_1_2)`; add `@Volatile var schedulesCache: List<Schedule> = emptyList()`; in `refreshRuleCache()` load schedules and set the cache; drop the sleep/work lines:

```kotlin
schedulesCache = database.schedules().all().map { it.toSchedule() }
```

`AppStores.kt`: `StoredSnapshot` gains `val schedules: List<Schedule>`; `read` loads them; add:

```kotlin
private val schedulesMutex = Mutex()

suspend fun saveSchedule(app: ElbowsUpApp, schedule: Schedule): List<Schedule> = schedulesMutex.withLock {
    app.database.schedules().upsert(schedule.toEntity())
    app.refreshRuleCacheAsync()
    app.database.schedules().all().map { it.toSchedule() }
}

suspend fun deleteSchedule(app: ElbowsUpApp, id: Long): List<Schedule> = schedulesMutex.withLock {
    app.database.schedules().delete(id)
    app.refreshRuleCacheAsync()
    app.database.schedules().all().map { it.toSchedule() }
}
```

`UiTime.kt`: `defaultSettings()` drops the preset fields:

```kotlin
fun defaultSettings(): SettingsEntity = SettingsEntity(
    timedUntilEpochMillis = null,
    untilResume = false,
    countryOverride = null,
    emptyBookOverride = false,
    setupComplete = false,
)
```

`ElbowsUpScreeningService.kt`: pause uses schedules:

```kotlin
val pauseActive = app.pauseClock?.let {
    isPaused(it, app.schedulesCache, nowMillis, snapshot.dayOfWeek, snapshot.minuteOfDay)
} ?: false
```

- [ ] **Step 4: Run the app tests and compile**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Compilation will also force every remaining user of the removed settings fields to be updated in later tasks; if the build reports them, fix the call sites now with the shapes defined here.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat: persist named schedules and migrate the v1 database"
```

---

### Task 3: Settings — schedule CRUD, system shortcuts, dialer warning

**Files:**
- Create: `app/src/main/kotlin/app/elbowsup/blocker/ui/ScheduleSheet.kt`
- Create: `app/src/main/kotlin/app/elbowsup/blocker/ui/SystemSettings.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/WindowFields.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/RulesTab.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/MainActivity.kt`
- Test: `app/src/test/kotlin/app/elbowsup/blocker/ui/ScheduleDraftTest.kt`
- Test: `app/src/test/kotlin/app/elbowsup/blocker/ui/RuleScheduleTextTest.kt`

**Interfaces:**
- Consumes: `Schedule`, `ScheduleAction`, `TimeWindow`, `DayOfWeek`.
- Produces: `ScheduleDraft(id, name, enabled, days, startText, endText, action)`, `validateScheduleDraft`, `draftFromSchedule`, `ScheduleDraft.toSchedule()`, `windowText(window): String`, `openScreeningSettings(context)`, `openDialerSettings(context)`, `SettingsScreen(initial, dialerHeld, screeningHeld, schedules, onOpenScreeningSettings, onOpenDialerSettings, onSaveSchedule, onDeleteSchedule, onSave)`.

- [ ] **Step 1: Write the failing tests**

`ScheduleDraftTest.kt`:

```kotlin
package app.elbowsup.blocker.ui

import app.elbowsup.blocker.engine.DayOfWeek
import app.elbowsup.blocker.engine.Schedule
import app.elbowsup.blocker.engine.ScheduleAction
import app.elbowsup.blocker.engine.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleDraftTest {
    @Test
    fun aNamedWindowBuildsASchedule() {
        val draft = ScheduleDraft(
            id = 0,
            name = " Work ",
            enabled = true,
            days = setOf(DayOfWeek.MONDAY),
            startText = "09:00",
            endText = "17:00",
            action = ScheduleAction.PAUSE,
        )

        val schedule = draft.toSchedule()!!

        assertEquals("Work", schedule.name)
        assertEquals(TimeWindow(setOf(DayOfWeek.MONDAY), 9 * 60, 17 * 60), schedule.window)
        assertEquals(ScheduleAction.PAUSE, schedule.action)
    }

    @Test
    fun aBlankNameOrNoDaysOrBadTimeIsRefused() {
        val valid = ScheduleDraft(name = "Work", days = setOf(DayOfWeek.MONDAY), startText = "09:00", endText = "17:00")
        assertNull(validateScheduleDraft(valid))
        assertNotNull(validateScheduleDraft(valid.copy(name = " ")))
        assertNotNull(validateScheduleDraft(valid.copy(days = emptySet())))
        assertNotNull(validateScheduleDraft(valid.copy(endText = "25:00")))
    }

    @Test
    fun draftFromScheduleRoundTrips() {
        val schedule = Schedule(4, "Sleep", false, TimeWindow(setOf(DayOfWeek.SATURDAY), 22 * 60, 7 * 60), ScheduleAction.PAUSE)
        assertEquals(schedule, draftFromSchedule(schedule).toSchedule())
    }
}
```

Update `RuleScheduleTextTest.kt`: keep `Always` and the custom-window case; delete the Sleep and Work assertions (those kinds no longer exist).

- [ ] **Step 2: Run the app tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: compilation failure — `ScheduleDraft`, `validateScheduleDraft`, and `draftFromSchedule` do not exist; `RuleScheduleTextTest` still references `ScheduleKind.SLEEP`.

- [ ] **Step 3: Write the implementation**

`ScheduleSheet.kt` — pure draft logic plus the Compose dialog:

```kotlin
package app.elbowsup.blocker.ui

import app.elbowsup.blocker.engine.DayOfWeek
import app.elbowsup.blocker.engine.Schedule
import app.elbowsup.blocker.engine.ScheduleAction
import app.elbowsup.blocker.engine.TimeWindow

data class ScheduleDraft(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val days: Set<DayOfWeek> = weekdays(),
    val startText: String = "09:00",
    val endText: String = "17:00",
    val action: ScheduleAction = ScheduleAction.PAUSE,
)

fun validateScheduleDraft(draft: ScheduleDraft): String? {
    if (draft.name.isBlank()) return "Give the schedule a name."
    if (draft.days.isEmpty()) return "Pick at least one day."
    if (parseMinute(draft.startText) == null || parseMinute(draft.endText) == null) {
        return "Use HH:MM for start and end."
    }
    return null
}

fun draftFromSchedule(schedule: Schedule): ScheduleDraft = ScheduleDraft(
    id = schedule.id,
    name = schedule.name,
    enabled = schedule.enabled,
    days = schedule.window.days,
    startText = formatMinute(schedule.window.startMinute),
    endText = formatMinute(schedule.window.endMinute),
    action = schedule.action,
)

fun ScheduleDraft.toSchedule(): Schedule? {
    if (validateScheduleDraft(this) != null) return null
    return Schedule(
        id = id,
        name = name.trim(),
        enabled = enabled,
        window = TimeWindow(days, parseMinute(startText)!!, parseMinute(endText)!!),
        action = action,
    )
}

@Composable
fun ScheduleSheet(
    draft: ScheduleDraft,
    onDraftChange: (ScheduleDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var error by remember(draft) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == 0L) "New schedule" else "Edit schedule") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                DayPicker(draft.days) { onDraftChange(draft.copy(days = it)) }
                MinuteField("Start", draft.startText) { onDraftChange(draft.copy(startText = it)) }
                MinuteField("End", draft.endText) { onDraftChange(draft.copy(endText = it)) }
                Text("Action")
                Text("Pause") // only action for now
                RowSwitch(
                    checked = draft.enabled,
                    label = "Enabled",
                    onCheckedChange = { onDraftChange(draft.copy(enabled = it)) },
                )
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val problem = validateScheduleDraft(draft)
                    if (problem != null) {
                        error = problem
                        return@TextButton
                    }
                    onSave(draft.toSchedule() ?: return@TextButton)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
```

`RowSwitch` must become non-private in `SettingsScreen.kt` (or move it to `WindowFields.kt`); move it to `WindowFields.kt` as a shared component.

`WindowFields.kt` gains:

```kotlin
fun windowText(window: TimeWindow): String {
    val days = window.days.sortedBy { it.ordinal }.joinToString(", ") { dayAbbrev(it) }
    return "$days ${formatMinute(window.startMinute)}–${formatMinute(window.endMinute)}"
}
```

Move `dayAbbrev` from `RulesTab.kt` to `WindowFields.kt` (or keep both; one definition). `RulesTab.ruleScheduleText` becomes `ALWAYS -> "Always"`, `CUSTOM -> rule.customWindow?.let { windowText(it) } ?: "Custom"`.

`SystemSettings.kt`:

```kotlin
package app.elbowsup.blocker.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager

fun openScreeningSettings(context: Context) {
    launchSettings(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}

fun openDialerSettings(context: Context) {
    launchSettings(
        context,
        Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName),
    )
}

private fun launchSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}
```

`SettingsScreen.kt` rewrite: `SettingsForm(country, emptyBookOverride)`; `toForm`/`applying` keep only those; delete `validateSettingsForm`; composable gains dialer/screening/schedules params and sections:

```kotlin
Text("Call screening app")
Text(if (screeningHeld) "ElbowsUp is the call screening app." else "ElbowsUp is not the call screening app. Blocking stays off until it is.")
Button(onClick = onOpenScreeningSettings) { Text("Open system settings") }

Text("Phone app (default dialer)")
if (dialerHeld) {
    Text("ElbowsUp is the phone app.")
} else {
    Text("ElbowsUp is not the phone app. Answer and hang up will not work; rules with that action reject instead.")
}
Button(onClick = onOpenDialerSettings) { Text("Open default dialer settings") }

Text("Schedules")
Text("A schedule pauses blocking during its window.")
schedules.forEach { schedule ->
    Row {
        Column(Modifier.weight(1f)) {
            Text(schedule.name)
            Text(windowText(schedule.window))
            Text("Pause")
        }
        Switch(checked = schedule.enabled, onCheckedChange = { onToggleSchedule(schedule) })  // via onSaveSchedule
        TextButton(onClick = { editingSchedule = draftFromSchedule(schedule) }) { Text("Edit") }
        TextButton(onClick = { onDeleteSchedule(schedule) }) { Text("Delete") }
    }
}
Button(onClick = { editingSchedule = ScheduleDraft() }) { Text("Add schedule") }
```

Schedule dialog state lives in `SettingsScreen`:

```kotlin
var editingSchedule by remember { mutableStateOf<ScheduleDraft?>(null) }
...
editingSchedule?.let { draft ->
    ScheduleSheet(
        draft = draft,
        onDraftChange = { editingSchedule = it },
        onDismiss = { editingSchedule = null },
        onSave = { schedule -> onSaveSchedule(schedule); editingSchedule = null },
        onDelete = if (draft.id == 0L) null else { { onDeleteSchedule(scheduleFromDraft) ; editingSchedule = null } },
    )
}
```

For the enabled switch, save immediately with `schedule.copy(enabled = it)`.

`MainActivity.kt` wiring in the settings branch:

```kotlin
showSettings -> SettingsScreen(
    initial = settings.toForm(),
    dialerHeld = dialerHeld,
    screeningHeld = screeningHeld,
    schedules = schedules,
    onOpenScreeningSettings = { openScreeningSettings(context) },
    onOpenDialerSettings = { openDialerSettings(context) },
    onSaveSchedule = { schedule -> scope.launch { schedules = AppStores.saveSchedule(app, schedule) } },
    onDeleteSchedule = { schedule -> scope.launch { schedules = AppStores.deleteSchedule(app, schedule.id) } },
    onSave = { form ->
        scope.launch {
            settings = AppStores.updateSettings(app) { it.applying(form) }
            showSettings = false
        }
    },
)
```

Add `var schedules by remember { mutableStateOf<List<Schedule>>(emptyList()) }`, load from `AppStores.read` (`schedules = loaded.schedules`), and pass `schedules` to `blockerStatus(...)`.

- [ ] **Step 4: Run the app tests and compile**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat: manage named schedules and open system settings from the settings screen"
```

---

### Task 4: Top bar status and attention banners

**Files:**
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/BlockerStatusText.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/MainActivity.kt`
- Test: `app/src/test/kotlin/app/elbowsup/blocker/ui/BlockerStatusTextTest.kt`

**Interfaces:**
- Produces: `blockerStatusText(status): String` returning `Active` / `Needs setup` / `Paused`; `blockerStatusDetail(status, untilLabel): String?` for banners.

- [ ] **Step 1: Write the failing test**

Replace `BlockerStatusTextTest.kt`:

```kotlin
package app.elbowsup.blocker.ui

import app.elbowsup.blocker.engine.BlockerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockerStatusTextTest {
    @Test
    fun theBarIsThreeSimpleWords() {
        assertEquals("Active", blockerStatusText(BlockerStatus.ACTIVE))
        assertEquals("Active", blockerStatusText(BlockerStatus.DIALER_MISSING))
        assertEquals("Needs setup", blockerStatusText(BlockerStatus.SETUP_INCOMPLETE))
        assertEquals("Needs setup", blockerStatusText(BlockerStatus.CONTACTS_UNREADABLE))
        assertEquals("Needs setup", blockerStatusText(BlockerStatus.SCREENING_MISSING))
        assertEquals("Paused", blockerStatusText(BlockerStatus.PAUSED_UNTIL))
        assertEquals("Paused", blockerStatusText(BlockerStatus.PAUSED_UNTIL_RESUME))
        assertEquals("Paused", blockerStatusText(BlockerStatus.PAUSED_SCHEDULE))
    }

    @Test
    fun detailsExplainEveryOffOrPausedState() {
        assertNull(blockerStatusDetail(BlockerStatus.ACTIVE, null))
        assertEquals("Blocking is off until setup is finished.", blockerStatusDetail(BlockerStatus.SETUP_INCOMPLETE, null))
        assertEquals("Blocking is off because contacts are not readable.", blockerStatusDetail(BlockerStatus.CONTACTS_UNREADABLE, null))
        assertEquals("Blocking is off because ElbowsUp is not the screening app.", blockerStatusDetail(BlockerStatus.SCREENING_MISSING, null))
        assertEquals(
            "Answer and hang up is unavailable because ElbowsUp is not the phone app. Those rules reject instead.",
            blockerStatusDetail(BlockerStatus.DIALER_MISSING, null),
        )
        assertEquals("Paused until 3:45 PM.", blockerStatusDetail(BlockerStatus.PAUSED_UNTIL, "3:45 PM"))
        assertEquals("Paused until you resume.", blockerStatusDetail(BlockerStatus.PAUSED_UNTIL_RESUME, null))
        assertEquals("Paused for a schedule.", blockerStatusDetail(BlockerStatus.PAUSED_SCHEDULE, null))
    }
}
```

- [ ] **Step 2: Run the app tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: compilation failure — `blockerStatusText` takes two arguments and `PAUSED_SCHEDULE` has no branch.

- [ ] **Step 3: Write the implementation**

`BlockerStatusText.kt`:

```kotlin
package app.elbowsup.blocker.ui

import app.elbowsup.blocker.engine.BlockerStatus

fun blockerStatusText(status: BlockerStatus): String = when (status) {
    BlockerStatus.ACTIVE, BlockerStatus.DIALER_MISSING -> "Active"
    BlockerStatus.SETUP_INCOMPLETE,
    BlockerStatus.CONTACTS_UNREADABLE,
    BlockerStatus.SCREENING_MISSING -> "Needs setup"
    BlockerStatus.PAUSED_UNTIL,
    BlockerStatus.PAUSED_UNTIL_RESUME,
    BlockerStatus.PAUSED_SCHEDULE -> "Paused"
}

fun blockerStatusDetail(status: BlockerStatus, untilLabel: String?): String? = when (status) {
    BlockerStatus.ACTIVE -> null
    BlockerStatus.SETUP_INCOMPLETE -> "Blocking is off until setup is finished."
    BlockerStatus.CONTACTS_UNREADABLE -> "Blocking is off because contacts are not readable."
    BlockerStatus.SCREENING_MISSING -> "Blocking is off because ElbowsUp is not the screening app."
    BlockerStatus.DIALER_MISSING -> "Answer and hang up is unavailable because ElbowsUp is not the phone app. Those rules reject instead."
    BlockerStatus.PAUSED_UNTIL -> {
        val label = untilLabel ?: "later"
        if (label.endsWith(".")) "Paused until $label" else "Paused until $label."
    }
    BlockerStatus.PAUSED_UNTIL_RESUME -> "Paused until you resume."
    BlockerStatus.PAUSED_SCHEDULE -> "Paused for a schedule."
}
```

`MainActivity.kt`: top bar uses `blockerStatusText(status)` with `maxLines = 1`; under the bar show a banner for `DIALER_MISSING` (after setup) with `blockerStatusDetail` and a button that calls `openDialerSettings(context)`. The existing `SetupCard`, `EmptyBookBanner`, and the contacts/screening prompts keep their copy; `PauseNotifications` uses `blockerStatusText(BlockerStatus.PAUSED_UNTIL_RESUME)` — update that call to the one-argument form.

- [ ] **Step 4: Run the app tests and compile**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat: show a simple active or needs-setup status with dialer detail"
```

---

### Task 5: Rule sheet — answer-hangup needs the dialer role; Keypad renamed Dialer

**Files:**
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/RuleSheet.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/RuleDraft.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/MainActivity.kt`
- Test: `app/src/test/kotlin/app/elbowsup/blocker/ui/RuleDraftTest.kt`

**Interfaces:**
- Consumes: `dialerHeld`.
- Produces: `answerHangupNote(dialerHeld): String?`; `RuleSheet(draft, dialerHeld, onDraftChange, onDismiss, onSave, onDelete)`; `DialerTab.DIALER("Dialer")`.

- [ ] **Step 1: Write the failing test**

Add to `RuleDraftTest.kt`:

```kotlin
    @Test
    fun answerHangupCarriesANoteOnlyWithoutTheDialerRole() {
        assertNull(answerHangupNote(dialerHeld = true))
        assertEquals(
            "ElbowsUp is not the phone app, so answer and hang up rejects instead. Set it as the default dialer in Settings.",
            answerHangupNote(dialerHeld = false),
        )
    }
```

- [ ] **Step 2: Run the app tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: compilation failure — `answerHangupNote` does not exist.

- [ ] **Step 3: Write the implementation**

`RuleDraft.kt`:

```kotlin
fun answerHangupNote(dialerHeld: Boolean): String? =
    if (dialerHeld) {
        null
    } else {
        "ElbowsUp is not the phone app, so answer and hang up rejects instead. Set it as the default dialer in Settings."
    }
```

`RuleSheet.kt`: add `dialerHeld: Boolean` parameter. Change `ChoiceMenu` options from `Pair<String, T>` to a small `Choice<T>(label, value, enabled = true)` type; render `DropdownMenuItem(enabled = option.enabled, ...)`. The action menu:

```kotlin
ChoiceMenu(
    label = "Action",
    value = if (draft.action == BlockAction.ANSWER_HANGUP) "Answer and hang up" else "Reject",
    options = listOf(
        Choice("Reject", BlockAction.REJECT),
        Choice("Answer and hang up", BlockAction.ANSWER_HANGUP, enabled = dialerHeld),
    ),
    onSelect = { onDraftChange(draft.copy(action = it)) },
)
answerHangupNote(dialerHeld)?.let { Text(it) }
```

`MainActivity.kt`: pass `dialerHeld = dialerHeld` to `RuleSheet`; rename `DialerTab.KEYPAD` to `DialerTab.DIALER` and its label to `"Dialer"`; update the two `DialerTab.KEYPAD` references.

- [ ] **Step 4: Run the app tests and compile**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat: gate answer-and-hangup on the dialer role and rename the keypad tab"
```

---

### Task 6: History — match blocked events, detail dialog, remove the Blocked tab

**Files:**
- Create: `app/src/main/kotlin/app/elbowsup/blocker/ui/BlockedMerge.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/AttemptBursts.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/HistoryTab.kt`
- Modify: `app/src/main/kotlin/app/elbowsup/blocker/ui/MainActivity.kt`
- Delete: `app/src/main/kotlin/app/elbowsup/blocker/ui/BlockedTab.kt`
- Test: `app/src/test/kotlin/app/elbowsup/blocker/ui/AttemptBurstsTest.kt`

**Interfaces:**
- Consumes: `HistoryRow`, `BlockedEventEntity`, `BlockAction`.
- Produces: `matchBlockedEvents(rows, events, maxGapMillis): Map<Long, BlockedEventEntity>`, `HistoryRow.isBlockedAttempt(matches): Boolean`, `historyBursts(rows, matches, maxGapMillis)`, `blockedActionLabel(action): String`, `HistoryTab(rows, events, callLogGranted, ruleIds, onRequestCallLog, onCallBack, onAddRule, onAllowNumber, onOpenRule)`.

- [ ] **Step 1: Write the failing test**

Replace `AttemptBurstsTest.kt`:

```kotlin
package app.elbowsup.blocker.ui

import android.provider.CallLog
import app.elbowsup.blocker.data.BlockedEventEntity
import app.elbowsup.blocker.engine.BlockAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptBurstsTest {
    private fun event(
        id: Long,
        time: Long,
        number: String? = "+14155551234",
        action: BlockAction = BlockAction.REJECT,
    ) = BlockedEventEntity(
        id = id,
        timeEpochMillis = time,
        number = number,
        displayName = null,
        action = action.name,
        ruleId = 1,
        ruleSummary = "rule",
    )

    private fun row(
        id: Long,
        time: Long,
        stored: String? = "+14155551234",
        type: Int = CallLog.Calls.BLOCKED_TYPE,
        duration: Int = 0,
    ) = HistoryRow(
        id = id,
        rawNumber = stored,
        storedNumber = stored,
        displayName = null,
        startEpochMillis = time,
        durationSeconds = duration,
        type = type,
    )

    @Test
    fun aRejectEventMatchesTheNearestBlockedRow() {
        val rows = listOf(row(2, 7_500), row(1, 5_000))
        val events = listOf(event(1, 5_200))

        val matches = matchBlockedEvents(rows, events)

        assertEquals(1, matches.size)
        assertEquals(1L, matches[1L]?.id)
        assertNull(matches[2L])
    }

    @Test
    fun aRowIsNeverMatchedTwice() {
        val rows = listOf(row(2, 7_500), row(1, 5_000))
        val events = listOf(event(1, 5_100), event(2, 7_600))

        val matches = matchBlockedEvents(rows, events)

        assertEquals(2, matches.size)
        assertEquals(1L, matches[1L]?.id)
        assertEquals(2L, matches[2L]?.id)
    }

    @Test
    fun aGapOverTwoMinutesOrADifferentNumberDoesNotMatch() {
        val rows = listOf(row(1, 0), row(2, 0, "+14155550000"))
        val events = listOf(event(1, 120_001), event(2, 5_000, "+14155551111"))

        assertTrue(matchBlockedEvents(rows, events).isEmpty())
    }

    @Test
    fun anAnswerHangupEventMatchesOnlyAShortIncomingRow() {
        val incoming = row(1, 5_000, type = CallLog.Calls.INCOMING_TYPE, duration = 1)
        val blocked = row(2, 5_000, type = CallLog.Calls.BLOCKED_TYPE)

        val matches = matchBlockedEvents(listOf(incoming, blocked), listOf(event(9, 5_100, action = BlockAction.ANSWER_HANGUP)))

        assertEquals(1, matches.size)
        assertEquals(9L, matches[1L]?.id)
    }

    @Test
    fun historyFoldsBlockedRowsAndAnswerHangupIncomingRows() {
        val rows = listOf(
            row(3, 7_600, type = CallLog.Calls.INCOMING_TYPE),
            row(2, 7_500),
            row(1, 5_000),
        )
        val events = listOf(
            event(3, 7_600, action = BlockAction.ANSWER_HANGUP),
            event(2, 7_600),
            event(1, 5_100),
        )
        val matches = matchBlockedEvents(rows, events)

        val bursts = historyBursts(rows, matches)

        assertEquals(1, bursts.size)
        assertEquals(listOf(3L, 2L, 1L), bursts[0].rows.map { it.id })
    }

    @Test
    fun historyNonBlockedRowEndsRun() {
        val rows = listOf(
            row(3, 10_000),
            row(2, 7_500, type = CallLog.Calls.INCOMING_TYPE, duration = 30),
            row(1, 5_000),
        )

        val bursts = historyBursts(rows, matchBlockedEvents(rows, listOf(event(1, 5_100), event(3, 10_100))))

        assertEquals(3, bursts.size)
    }

    @Test
    fun historyDifferentNumberOrNullNumberNeverFolds() {
        val different = listOf(
            row(3, 10_000, "+14155550000"),
            row(2, 7_500, "+14155551111"),
            row(1, 5_000, "+14155550000"),
        )
        assertEquals(3, historyBursts(different, matchBlockedEvents(different, emptyList())).size)

        val nulls = listOf(row(2, 7_500, null), row(1, 5_000, null))
        assertEquals(2, historyBursts(nulls, matchBlockedEvents(nulls, emptyList())).size)
    }

    @Test
    fun gapOverTwoMinutesStartsNewBurst() {
        val rows = listOf(row(2, 120_001), row(1, 0))
        assertEquals(2, historyBursts(rows, matchBlockedEvents(rows, emptyList())).size)
    }

    @Test
    fun exactlyTwoMinutesApartFolds() {
        val rows = listOf(row(2, 120_000), row(1, 0))
        assertEquals(1, historyBursts(rows, matchBlockedEvents(rows, emptyList())).size)
    }

    @Test
    fun blockedActionLabelNamesBothActions() {
        assertEquals("Reject", blockedActionLabel(BlockAction.REJECT.name))
        assertEquals("Answer and hang up", blockedActionLabel(BlockAction.ANSWER_HANGUP.name))
        assertFalse(HistoryRow(1, null, null, null, 0, 0, CallLog.Calls.INCOMING_TYPE).isBlockedAttempt(emptyMap()))
    }
}
```

- [ ] **Step 2: Run the app tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: compilation failure — `matchBlockedEvents`, `blockedActionLabel`, and the new `historyBursts` shape do not exist.

- [ ] **Step 3: Write the implementation**

`BlockedMerge.kt`:

```kotlin
package app.elbowsup.blocker.ui

import android.provider.CallLog
import app.elbowsup.blocker.data.BlockedEventEntity
import app.elbowsup.blocker.engine.BlockAction
import kotlin.math.abs

const val BLOCKED_MATCH_WINDOW_MS = 2 * 60_000L

fun matchBlockedEvents(
    rows: List<HistoryRow>,
    events: List<BlockedEventEntity>,
    maxGapMillis: Long = BLOCKED_MATCH_WINDOW_MS,
): Map<Long, BlockedEventEntity> {
    if (rows.isEmpty() || events.isEmpty()) return emptyMap()
    val oldest = rows.minOf { it.startEpochMillis } - maxGapMillis
    val newest = rows.maxOf { it.startEpochMillis } + maxGapMillis
    val usedRows = mutableSetOf<Long>()
    val matches = mutableMapOf<Long, BlockedEventEntity>()
    for (event in events) {
        if (event.timeEpochMillis !in oldest..newest) continue
        val expectedType = when (event.action) {
            BlockAction.REJECT.name -> CallLog.Calls.BLOCKED_TYPE
            BlockAction.ANSWER_HANGUP.name -> CallLog.Calls.INCOMING_TYPE
            else -> continue
        }
        val candidate = rows
            .filter { it.id !in usedRows && it.type == expectedType && it.storedNumber == event.number }
            .minByOrNull { abs(it.startEpochMillis - event.timeEpochMillis) }
            ?: continue
        if (abs(candidate.startEpochMillis - event.timeEpochMillis) > maxGapMillis) continue
        usedRows += candidate.id
        matches[candidate.id] = event
    }
    return matches
}

fun blockedActionLabel(action: String): String = when (action) {
    BlockAction.REJECT.name -> "Reject"
    BlockAction.ANSWER_HANGUP.name -> "Answer and hang up"
    else -> action
}
```

`AttemptBursts.kt`: delete `BlockedBurst`/`blockedBursts`; `HistoryBurst` stays; `historyBursts(rows, matches, maxGapMillis)`; add:

```kotlin
fun HistoryRow.isBlockedAttempt(matches: Map<Long, BlockedEventEntity>): Boolean =
    storedNumber != null &&
        (type == CallLog.Calls.BLOCKED_TYPE || matches[id]?.action == BlockAction.ANSWER_HANGUP.name)
```

and the join condition uses `row.isBlockedAttempt(matches) && previous.isBlockedAttempt(matches) && row.storedNumber == previous.storedNumber && previous.startEpochMillis - row.startEpochMillis <= maxGapMillis`.

`HistoryTab.kt`: compute `matches` and `bursts` with `remember`; rows become clickable; hold `var detail by remember { mutableStateOf<HistoryBurst?>(null) }`; render `HistoryDetailDialog`. Summary label prefixes blocked rows with `Blocked · <action>`; the dialog shows:

```kotlin
AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(nameOrNumber) },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(burstSummaryLabel(burst, matches))
            if (burst.rows.size > 1) {
                burst.rows.forEach { attempt ->
                    val event = matches[attempt.id]
                    Text("${callTypeLabel(attempt.type)} · ${formatLocalDateTime(attempt.startEpochMillis)}" + (event?.let { " · ${blockedActionLabel(it.action)}" } ?: ""))
                }
            }
            val event = matches[burst.rows.first().id]
            if (event != null) {
                Text("Blocked by ElbowsUp")
                Text(blockedActionLabel(event.action))
                if (event.ruleSummary.isNotBlank()) Text(event.ruleSummary)
            }
        }
    },
    confirmButton = {
        Row {
            val dial = ...
            if (dial != null) Button(onClick = { onCallBack(dial) }) { Text("Call back") }
            Button(onClick = { onAddRule(newest) }) { Text("Add rule") }
            if (event?.number != null) Button(onClick = { onAllowNumber(event.number) }) { Text("Allow this number") }
            if (event?.ruleId != null && event.ruleId in ruleIds) Button(onClick = { onOpenRule(event.ruleId) }) { Text("Open rule") }
        }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
)
```

`MainActivity.kt`: drop the `BLOCKED` tab and its branch, pass `events = blocked` and the `onAllowNumber`/`onOpenRule` callbacks into `HistoryTab`, and remove the `BlockedTab` import. The allow-number callback moves unchanged.

- [ ] **Step 4: Delete the Blocked tab and run the tests**

```bash
rm app/src/main/kotlin/app/elbowsup/blocker/ui/BlockedTab.kt
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A app/src
git commit -m "feat: fold the blocked list into a history detail dialog"
```

---

### Task 7: Spec, verification, and device notes

**Files:**
- Modify: `docs/superpowers/specs/2026-09-26-elbowsup-call-blocker-design.md`

- [ ] **Step 1: Update the spec**

Edit the spec so code and spec agree: schedules section (named schedules replace Sleep/Work/recurring pause; rule schedules are Always or Custom; action currently Pause only), pause section, screens (no Blocked tab; History tap opens details; simple status line; settings shortcuts and dialer warning), stored data (schedules table; settings fields), testing list, and the dialer-loss copy.

- [ ] **Step 2: Full verification**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 3: Commit**

```bash
git add docs
git commit -m "docs: sync the design spec with schedules, status, and history changes"
```

## Device checks (not run here)

Call-path behavior changed (dialer-loss reject fallback path and the pause clock), and the v1→v2 migration has no unit test harness in this repo. On a device: install over a v1 build and confirm rules and pause survive; set ElbowsUp as screening only and confirm an answer-hangup rule rejects and History shows Reject; make it the dialer and confirm answer-and-hangup; check the Settings shortcuts open the system pages.
