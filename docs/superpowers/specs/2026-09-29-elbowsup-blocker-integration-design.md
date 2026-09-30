# Elbows Up — Call Blocker in the Fossify Dialer: Design and Phase Plan

- **Date:** 2026-09-29
- **Status:** DRAFT — decisions D1–D4 and v1 scope settled (§7, §9); Phase 0 spikes done (`docs/superpowers/notes/2026-09-29-phase0-spike-results.md`); local only (not filed as issues); no product code yet
- **Scope:** add a rule-based call blocker to this Fossify Phone fork, so one app is both the default dialer and the call blocker.
- **Reference only:** the standalone POC at `/Users/chrisc/workspace/spam-elbowsup` (and `docs/superpowers/notes/`). It informs intent and pitfalls (§2). It is **not** a source to port: architecture, storage, UI, and behavior are designed here for this codebase.

## 1. Goal and principles

Elbows Up is Fossify Phone plus a blocker: the user writes ordered rules; matching calls are rejected, silenced, or answered and hung up. The blocker has to run in the dialer because the carrier caller name (CNAM, e.g. "Likely Spam") is only visible to the default dialer's in-call service, never in call screening.

1. **Fail open.** Any uncertainty lets the call ring. A block is only the deliberate result of a matching rule.
2. **Never miss a call because of us.** Telecom does the ringing (D3, revised in Phase 6). Ringing works with the blocker off, unconfigured, or crashed, because we never touch it unless a rule matches.
3. **Offline.** No `INTERNET`, no network, no spam database.
4. **Minimal upstream diff.** New code in new files; upstream edits are single-call hooks, logged in a ledger (§6).
5. **Match Fossify.** Views + ViewBinding, commons dialogs/adapters/theming, Fossify naming and detekt rules. No new UI toolkit (D1).

## 2. What the POC teaches (reference, not inheritance)

Product intent worth keeping as *ideas*, each re-decided in §7:

- Ordered first-match rules; contacts and emergency numbers always allowed; a pause switch; a small set of block actions; a log of what was blocked and why.

Pitfalls the POC hit or hid, which shape this design:

| Finding | Consequence here |
|---|---|
| Screening sees **no caller name** (frozen `Call.Details`, never mutates); the name is present when the call reaches the in-call service, 77–290 ms later (spikes). | Name-based rules cannot be decided in screening. An "empty name" rule evaluated there would match every non-contact call. → two-stage evaluation (§4.A). |
| On Rogers an unlabeled caller's name is **the number itself**, not blank. | "Empty name" means no real name: blank, or digits equal to the call's number (§4.A). |
| Telecom's ring is already going when the in-call service sees the name. | Measured in Phase 6: Telecom holds its ring about a second for the dialer to be ready, so a reject that arrives ~0.5 s after the call is never heard. A late silence must be repeated until the ring has started (§4.B). |
| Rules/contacts were cached asynchronously after process start, so the **first call to a cold process failed open** and was never blocked. | Storage chosen so a cold start reads rules synchronously in milliseconds (§5). |
| Answer-and-hangup was keyed on `Call.Details.getId()`, which is **API 35**, with minSdk 29. Below 35 it throws `NoSuchMethodError` (an `Error`, not caught by `catch (Exception)`). Here the dialer decides the call itself, so no id is needed at all. | Explicit SDK gating (§4.D). |
| Silence combined with disallow is illegal; a disallowed call never reaches the dialer. | Action → `CallResponse` mapping is a tested pure function. |
| Contact numbers normalized under a different region silently stop matching. | Reuse Fossify's phone-number comparison for contacts instead of a home-grown set (§4.E). |

Deliberately **not** carried over: Compose UI, a separate in-call UI/service, Room + migrations, the module split, the keypad, the tab shell.

## 3. Architecture

Everything new is in `:app`, package `com.keejii.elbowsup` (upstream stays `org.fossify.phone`; we never add files there). Resources use an `elbowsup_` prefix; strings go in `strings_elbowsup.xml`, English only.

| Piece | Role |
|---|---|
| `core` (pure Kotlin, no Android imports) | Rule model, matching, ordered evaluation with a stage, pause/schedule check, decision → `CallResponse` flags. Unit-tested on the JVM (`testFossDebugUnitTest`; `src/test` and JUnit are added in Phase 1). |
| `BlockerConfig` | Own `SharedPreferences` file with Gson JSON, following Fossify's speed-dial precedent. **Not** an edit to upstream `Config.kt`. Holds rules, schedules, pause, setup flag. |
| `EventLog` | Small capped store of blocked events (time, number, name, action, rule summary). Backing is a JSON-lines file (`filesDir/elbowsup/blocked_events.jsonl`, cap 500): one cheap append on the call path, no schema; appending never throws, so a full disk cannot change what happens to a call. |
| `BlockerRuntime` | Lazy singleton created by the hooks: loads config, evaluates, records events. No `Application` subclass, no manifest change for init. |
| `BlockerScreening` | Early stage, called from the screening hook. |
| `BlockerCalls` | Dialer stage: late evaluation and actions, called from the `CallService` hook. |
| `ui` | `BlockerActivity` and dialogs in views, styled like Fossify's own settings and recents screens. |

Engine as a separate Gradle module was considered and rejected for now: a pure package in `:app` needs fewer upstream build edits and can be extracted later.

## 4. The call pipeline

### A. Two-stage evaluation

Same ordered rule list, evaluated at two points; first match wins at both.

- **Early (screening):** name unknown. Number-only rules (exact, prefix, no-number) decide immediately and can use every action, including silent ones, with no ring. If the walk reaches a **name-dependent** rule whose other conditions hold, the result is `DEFER`: screening allows, and the call is marked for the late stage.
- **Late (in-call service):** on `onCallAdded` and `onDetailsChanged`, re-run the full list with the name filled in.
- Spikes showed the name is present at `onCallAdded` on every call and never changed afterwards, so there is **no settle window**: decide at `onCallAdded`, and re-evaluate on `onDetailsChanged` while the call is still ringing.
- **"Empty name"** matches a call with a dialable number whose name is blank or is just that number's digits (Rogers echoes the number for unlabeled callers).

### B. Telecom rings; we decide (D3, revised)

We do not own ringing. Owning it (the `IN_CALL_SERVICE_RINGING` flag and our own `Ringer`) was built and tried in Phase 5, then dropped in Phase 6. Telecom's own log showed it is enough for this app, and owning ringing cannot ring in Priority-only Do Not Disturb with starred-only callers (for example the default Sleeping mode): Android mutes ringtone audio and vibration there for every app except four system packages named in `config_priorityOnlyDndExemptPackages`, and a dialer cannot add itself to that list. Our ringer decided correctly (the platform's own call filter allowed the starred contact) but played into that mute.

What Phase 6 measured with Telecom ringing (caiman, Android 17):

- **Warm process, name rule, reject:** Telecom starts the ring about 0.3 s after the call reaches the dialer and delays the ringtone about a second for the dialer's ready signal. The reject arrived ~0.5 s after the call and no ringtone ever played: no audible ring.
- **Cold process, fork not holding the screening role:** the dialer service took ~1.6 s to start and about 0.6 s of ring was heard. This does not arise in normal use, because blocking only runs with the screening role and screening starts our process for every call: with the role held, a cold-process call was rejected before the ringtone played.
- **Late silence:** `TelecomManager.silenceRinger()` is allowed for the default dialer and is accepted, but Telecom ignores one that arrives before it has started ringing. `BlockerCalls` therefore repeats it every 0.3 s for up to 3 s while the call is still ringing; the ringtone was stopped ~0.5 s after the call, at most a short blip.
- **Screening-time silence and answer-and-hang-up** use `EXTRA_SILENT_RINGING_REQUESTED`, which Telecom itself honors, so no dialer code is needed for them.
- **Fail-safe:** any failure in the dialer stage does nothing, and Telecom keeps ringing. Do Not Disturb, ringer modes, Bluetooth, second calls and ringtones are all Telecom's, so there is no ringing matrix for us to maintain.

Late actions: reject → `call.reject(false, null)` (platform logs it as `REJECTED_TYPE`, not blocked); silence → repeated `silenceRinger()`, the incoming screen still answers; answer and hang up → answer, then disconnect.

### C. Coexistence with Fossify's blocking (D2)

Upstream `SimpleCallScreeningService` already blocks from the system blocked-number list, "block unknown" and "block hidden", and a package can bind only one screening service. Order in the hook: our early stage first; if it does not produce an explicit verdict, fall through to the upstream code unchanged. Upstream settings and UI stay as they are.

### D. Android version gating

Facts from the SDK's API data: `isEmergencyNumber`, `setSilenceCall`, `RoleManager`, `getCallDirection` are 29; Fossify's minSdk is 26. We do not raise minSdk (that edits a dependabot-churned line). The blocker is off below 29. Answer and hang up needs only the dialer role, because the dialer re-evaluates the same rules itself and needs no `Call.Details.getId()` handoff (API 35) between screening and the in-call service; without the dialer role it behaves as Reject, hide missed call. Gate with explicit `SDK_INT` checks, never try/catch.

### E. Contacts

"Contact" means what Fossify treats as one: reuse `SimpleContactsHelper.existsSync(number, privateCursor)`, which upstream screening already calls and which covers private contacts and Android's number comparison. `existsSync` is a single indexed `PhoneLookup` query, so no timeout thread is used; it reports Found, NotFound or Undetermined, and Undetermined or any exception counts as a contact, so an unreadable book never blocks anything. Whether to add a "contacts unreadable/empty → pause blocking" safeguard (relevant on GrapheneOS contact scopes) is a v1 scope question (§7).

### F. Setup and roles

Fossify already prompts for default dialer. The blocker adds the call-screening role request and a status row in `BlockerActivity`. Blocking stays off until roles are granted; the app says what is missing.

### G. Caller name (CNAM) on the call screen

Today the incoming-call screen and notification show only the number: `getCallContact()` (`helpers/CallContactHelper.kt`) matches local contacts and, on no match, sets the name to the number; it never reads `Call.Details.callerDisplayName`. That one function feeds the call screen, the call notification, the on-hold name, and the conference list, so one small edit covers all of them.

- **Rule:** contacts always win. Only when there is no contact match, and the call carries a *real* carrier name, use it as the display name. The number still shows on the line below (the existing screen already does that when name and number differ).
- **Real name** is the same definition the "empty name" rule uses, as one shared pure function `realCallerName(name, presentationAllowed, number)` in `core`: non-blank, presentation allowed (not restricted or unknown), and not merely the number's digits. Rogers echoes the number for unlabeled callers (Phase 0), and that must not be shown as a name. Digit comparison ignores formatting and a country prefix.
- **Incoming calls only.** Outgoing calls and Recents rows are unchanged in v1. Showing the label in Recents is a Phase 7 candidate.
- **Timing:** the name is present at `onCallAdded`, before the screen or notification is built, so the first lookup already has it. For carriers that deliver it late, an optional few-line hook refreshes the screen and notification when the name changes while ringing; keep it only if a device shows a late name.
- **No marker** that the name is carrier-supplied in v1. A network name can be wrong or spoofed, and it looks like a contact name apart from the missing photo. Say if you want a visible "caller ID" tag.
- Independent of the blocker and its roles: it works for any call the dialer receives.

## 5. Storage and cold start

Rules are tens of items, so they live as JSON in `BlockerConfig` and load synchronously in milliseconds when a cold process is bound for screening. That removes the POC's cold-start race without a snapshot layer or a Room/KSP dependency. Blocked events go to a capped `EventLog` (write before responding, delete if the response throws). Phase 0 confirmed the screening service can be bound to a cold process (about 1 s old at screening), so the synchronous read has to work at that moment.

## 6. Touch-point ledger

Every upstream file we edit, marked `// ELBOWSUP` in the source and listed in `FORK.md`. Rules: no reformatting, moves, or renames of upstream files; hooks are single calls into our code.

| Upstream file | Change | Phase |
|---|---|---|
| `app/build.gradle.kts` | `testImplementation` JUnit (1 line) | 1 |
| `helpers/CallContactHelper.kt` | in the no-contact-match branch, prefer the real carrier name over the number (1 line and an import, plus a new `CarrierName.kt`; no string) | 1 |
| `activities/CallActivity.kt` (optional) | refresh caller info when the name changes while ringing (~4 lines); only if a late name is observed | 1 |
| `services/SimpleCallScreeningService.kt` | first statement of `onScreenCall`: delegate, return if handled (~3 lines) | 3 |
| `services/CallService.kt` | `onCallAdded` after `super`: blocker hook that may claim the call and skip UI (import plus 1 line) | 5 |
| `AndroidManifest.xml` | one contiguous block: `BlockerActivity` | 4 |
| `res/menu/menu.xml`, `MainActivity.kt` | one "Call blocker" item and its handler (~4 lines) | 4 |
| `adapters/RecentCallsAdapter.kt` | one line in the row binding that annotates the time text (import plus 1 line) | 7 |

## 7. v1 scope (confirmed 2026-09-29)

The POC's feature list is not inherited. v1:

- **In:** contacts + emergency always allowed; ordered rules, allow and block; matchers exact number, prefix, no-number, name wildcard (CNAM), empty name; per-rule schedule Always or a custom window (days, start, end; may cross midnight, the start day owns the spill); actions Reject, hide missed call (default), reject, silence, answer and hang up (35+); timed pause (15 min / 1 h / until resume) with a resume notification; named schedules that pause blocking during their window (name, days, start, end, enabled); blocked-event log; screening-role setup and status; blocked annotation in Recents.
- **Deferred:** regex matchers (and their 50 ms time budget), the "no readable contacts → pause" safeguard and country override, folding runs of attempts in Recents, area-code/prefix "chips" from the call log.

## 8. Phases

Each phase ends with a green `./gradlew assembleFossDebug testFossDebugUnitTest` and an updated ledger.

**Phase 0 — Spikes (done 2026-09-29; results in the notes file)**
1. Ringing takeover: scratch build with `IN_CALL_SERVICE_RINGING=true`; confirm Telecom stops ringing; prototype both `Ringer` options; check `EXTRA_SILENT_RINGING_REQUESTED`, `onSilenceRinger`, DND and ringer modes, headset routing. (The takeover worked in the normal case but was dropped in Phase 6, see §4.B.)
2. CNAM timing: caller name arrival versus `onCallAdded`/`onDetailsChanged` for labeled and unlabeled calls (a contact call was not tested); whether a name ever arrives late.
3. Cold start: kill the process, call, confirm the first call is screened.
- Exit met: no settle window, empty-name semantics corrected. A `Ringtone` + vibrator ringer worked here but was later replaced by Telecom's ringing (Phase 6, §4.B).

**Phase 1 — Core and caller name on the call screen:**
- *Core:* rule model, matching, two-stage ordered evaluation with `DEFER`, time windows (midnight-crossing, start inclusive/end exclusive), rule schedules, named pause schedules, timed pause check, decision → flags mapping, and `realCallerName`, with tests (add `src/test`). No Android.
- *CNAM slice (§4.G):* `CarrierName` adapter from `Call.Details` to `realCallerName`, the one-line `getCallContact()` edit, and the ledger entry. Needs no roles beyond the dialer, so it is the first thing to reach a device.
- Status 2026-09-29: core, normalization and the CNAM edit are implemented on branch `phase1-core-cnam` (55 unit tests, detekt clean). Device-verified on caiman: "Likely Spam" shows with the number below it on the notification and, with the phone locked, on the full call screen; an unlabeled caller shows just the number; a saved contact shows its contact name. Fossify's default of a heads-up notification (not full screen) on an unlocked phone is kept unchanged.
- Exit: unit tests pass; on device a call from 226-220-1235 shows "Likely Spam" with the number below, a call from 226-220-1234 still shows just the number, and a saved contact still shows its contact name.

**Phase 2 — Storage and runtime:** `BlockerConfig`, `EventLog`, `BlockerRuntime`, contact check, SDK gating.
- Status 2026-09-29: implemented on the same branch (80 unit tests total, detekt and lint clean). Rules and schedules are stored as JSON with explicit DTO mapping, and an unreadable or newer-version entry is dropped instead of guessed at. The phone-facing pieces (`BlockerConfig`, `BlockerRuntime`, contact and region lookups in `telecom/DeviceState.kt`) are thin wrappers over tested pure code and get their first device run in Phase 3. Cold-start behaviour is settled by design: `BlockerRuntime.get()` loads synchronously on first use.

**Phase 3 — Early stage:** `BlockerScreening` and the screening hook; number rules, contacts, emergency, pause, screening-time actions, fallthrough to upstream.
- Status 2026-09-29: implemented (94 unit tests; detekt and lint clean). `planScreening` is pure and tested; `telecom/BlockerScreening.kt` is the Android layer, and `services/SimpleCallScreeningService.kt` gained the one-line hook. With no UI yet, a debug-only receiver (`app/src/debug`, protected by the `DUMP` permission) seeds rules from adb.
- Device-verified on caiman: a quiet reject blocked the first call after a force-stop (cold process) with no ring, notification or call screen, and left a blocked call-log row; silence rang nothing but stayed answerable and left a missed-call row; a notifying reject produced a missed-call notification and a blocked row; a call matching no rule rang normally. Each block wrote its event with rule summary. Not device-tested: contact and emergency numbers (unit-tested; the lookups are Fossify's own `existsSync` and `isEmergencyNumber`) and answer-and-hang-up (Phase 5).
- Exit (device): a prefix rule rejects, silences, rejects quietly; a contact rings; emergency places; first call after process kill is screened.

**Phase 4 — Minimal UI:** menu entry, `BlockerActivity` with rules list, rule editor (including per-rule schedule), named schedules, role setup, pause and status.
- Status 2026-09-29: implemented as one scrolling `BlockerActivity` (status headline, pause/resume, setup rows, ordered rules with up/down reordering and an enabled switch, named pause schedules), a rule editor and a schedule editor, and an ongoing pause notification with a Resume action. Drafts, list editing and status are pure and unit-tested (123 tests total); detekt and lint are clean. Setup completes on its own the first time the screening role and contacts permission are both held.
- Design notes: up/down arrows replace drag-to-reorder; a prefix typed without a plus is read in the phone's own country; a screen-wide change listener on `BlockerRuntime` keeps the screen in step when the notification's Resume changes the state.
- Verified on the QA phone by driving the real screens: opening from the menu, add/edit/delete a rule, reordering, validation of bad input, the country-code prefix, the name-rule note, adding a schedule, pausing until resume, and Resume from the notification (which caught and fixed a stale screen). Then run end to end on caiman: the screening-role request from the screen completed setup, a quiet-reject prefix rule created in the UI blocked a real call, and the event log and call log both recorded it.
- Exit: rules created from the UI drive Phase 3 behavior.

**Phase 5 — Dialer stage:** late evaluation, name rules, answer-and-hang-up, `CallService` hook.
- Status 2026-09-29: implemented, then simplified in Phase 6 (§4.B). Pure `planDialer` is tested (136 unit tests; detekt and lint clean, no lint findings in new code); `telecom/BlockerCalls.kt` is the Android layer, and `CallService` gained one hook line. There is no ringer and no manifest flag.
- Design notes: no answer-and-hang-up handoff is needed (see §4.D); the dialer re-runs the whole list, so a call silenced at screening is recognised by `EXTRA_SILENT_RINGING_REQUESTED` and not recorded twice. A reject or answer-and-hang-up on the first look claims the call so Fossify never shows it; a later name change re-evaluates while ringing. Any failure leaves the call to Telecom, and the event is taken back.
- Device-verified on caiman (Android 17): a `Likely*` quiet reject left no audible ring, no screen and a logged event with the name; answer-and-hang-up answered (3 s call in the log) with nothing shown on the phone; a name silence stayed answerable and stopped Telecom's ring within ~0.5 s; a screening-time number silence stayed quiet and was recorded once, not twice.
- A late reject is logged by the platform as rejected, not blocked, so it appears in Recents as an ordinary rejected call until Phase 7.
- Exit: a labeled call is blocked and logged; an unlabeled call rings; ringing is untouched with the blocker off; a claimed call never shows the incoming screen.

**Phase 6 — Ringing matrix (done; it changed the design):** run against our own ringer on caiman. Cold start, vibrate mode and silent mode passed, but Priority-only Do Not Disturb with starred-only callers (the default Sleeping mode) failed: a starred contact did not ring, and no third-party dialer can be exempted from Android's mute (§4.B). We replaced the takeover with Telecom's ringing, removed `Ringer`, `ringPolicy` and the manifest flag, and measured the ring leak instead. Not tested with Telecom ringing: Bluetooth, second call, per-contact ringtones, because Telecom owns all of them.

**Phase 7 — Recents integration:** blocked annotation in Recents, and a recent-blocked-calls list with an "allow this number" action on the blocker screen.
- Status 2026-09-29: implemented (145 unit tests; detekt and lint clean, no lint findings in Kotlin; the new strings only add the usual missing-translation warnings). `storage/BlockedMatch.kt` pairs a call-log row with an event by normalized number and start time (event within −2 s to +20 s of the row, closest wins, newest on a tie) and is unit-tested. `ui/BlockedRecents.kt` appends " • Blocked" to a row's time text, plus the rule summary in the call-details list, and does nothing for outgoing rows; a row of the platform's own blocked type shows "Blocked" even with no event. The only upstream edit is one line in `RecentCallsAdapter.bind`.
- Design notes: a late reject or answer-and-hang-up is logged by the platform as an ordinary rejected or answered call, so only the event log can identify it, which is why rows are matched by number and time and not by call type. Grouped rows show the annotation for the group's newest call. "Allow this number" adds an exact-number allow rule at the top of the rules; "Remove from list" and "Clear list" edit only our event log. Actions live on the blocker screen and not in Recents, so Recents needed no menu or dialog edits.
- Device-verified on caiman with its real events and call log: Recents marked the 226-220-1236 group and the earlier blocked 1235 and 1234 groups as blocked and left your starred contact and ordinary missed calls plain; the blocker screen listed recent blocked calls with name, number, time and rule summary; "Allow this number" saved an allow rule that appeared in Rules; "Remove from list" dropped the entry. Not device-verified: the rule summary in the call-details dialog (in Fossify, tapping a Recents row calls the number back, and the dialog is reached from a menu I did not drive) and "Clear list" (unit-tested).
- Exit: a blocked call shows in Recents and can be allowed from the blocker screen.

**Phase 8 — Hardening and docs:** device matrix, `git merge upstream/main` rehearsal on a scratch branch, `FORK.md` ledger, `CONTEXT.md` glossary, ADRs (two-stage evaluation, letting Telecom ring and why owning it fails under Do Not Disturb, touch-point policy).

## 9. Decisions and open items

Settled:

- **D1 UI:** views, matching Fossify. Design each screen natively, not as a copy of the POC.
- **D2 Fossify's built-in blocking:** keep untouched, ours runs first (§4.C).
- **D3 Ring leak:** revised. We first chose to own ringing; Phase 6 showed Telecom's own ringing leaks nothing audible when the blocker runs (§4.B), so Telecom rings.
- **D4 Old app:** retired; no migration or export/import.

Open:


## 10. Risks

- **Late silence is timing-based:** it relies on repeating `silenceRinger()` until Telecom's ring has started. If a future Android release changes when Telecom starts ringing, a name-based silence could ring longer; a reject and a screening-time silence are unaffected.
- **A late reject is not a block in the platform log:** it shows as a rejected call in Recents (Phase 7 annotates it from our event log).
- **`CallService` hook** can regress the upstream call UI if the claim path skips setup wrongly; keep the skipped path tiny.
- **Recents rows are matched by number and time, not by call type.** Two blocked calls from one number within a few seconds could pair with the wrong event; the summary shown may then be the neighbouring call's.
- **License:** Fossify Phone is GPL-3.0, so all code here is GPL-3.0; keep attribution intact.
