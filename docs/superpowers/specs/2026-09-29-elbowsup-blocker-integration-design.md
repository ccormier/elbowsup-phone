# Elbows Up — Call Blocker in the Fossify Dialer: Design and Phase Plan

- **Date:** 2026-09-29
- **Status:** DRAFT — decisions D1–D4 and v1 scope settled (§7, §9); Phase 0 spikes done (`docs/superpowers/notes/2026-09-29-phase0-spike-results.md`); local only (not filed as issues); no product code yet
- **Scope:** add a rule-based call blocker to this Fossify Phone fork, so one app is both the default dialer and the call blocker.
- **Reference only:** the standalone POC at `/Users/chrisc/workspace/spam-elbowsup` (and `docs/superpowers/notes/`). It informs intent and pitfalls (§2). It is **not** a source to port: architecture, storage, UI, and behavior are designed here for this codebase.

## 1. Goal and principles

Elbows Up is Fossify Phone plus a blocker: the user writes ordered rules; matching calls are rejected, silenced, or answered and hung up. The blocker has to run in the dialer because the carrier caller name (CNAM, e.g. "Likely Spam") is only visible to the default dialer's in-call service, never in call screening.

1. **Fail open.** Any uncertainty lets the call ring. A block is only the deliberate result of a matching rule.
2. **Never miss a call because of us.** We will own ringing (D3). Ringing must work with the blocker off, unconfigured, or crashed.
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
| Telecom's ring is already going when the in-call service sees the name. | We own ringing, so nothing rings until the late decision (§4.B, D3). Verified: a late reject makes no sound. |
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
| `BlockerCalls` + `Ringer` | Dialer stage: late evaluation, handoff, and ringing, called from the `CallService` hook. |
| `ui` | `BlockerActivity` and dialogs in views, styled like Fossify's own settings and recents screens. |

Engine as a separate Gradle module was considered and rejected for now: a pure package in `:app` needs fewer upstream build edits and can be extracted later.

## 4. The call pipeline

### A. Two-stage evaluation

Same ordered rule list, evaluated at two points; first match wins at both.

- **Early (screening):** name unknown. Number-only rules (exact, prefix, no-number) decide immediately and can use every action, including silent ones, with no ring. If the walk reaches a **name-dependent** rule whose other conditions hold, the result is `DEFER`: screening allows, and the call is marked for the late stage.
- **Late (in-call service):** on `onCallAdded` and `onDetailsChanged`, re-run the full list with the name filled in.
- Spikes showed the name is present at `onCallAdded` on every call and never changed afterwards, so there is **no settle window**: decide at `onCallAdded`, and re-evaluate on `onDetailsChanged` while the call is still ringing.
- **"Empty name"** matches a call with a dialable number whose name is blank or is just that number's digits (Rogers echoes the number for unlabeled callers).

### B. Owning the ringing (D3)

We declare `android.telecom.IN_CALL_SERVICE_RINGING=true`. Telecom then stops ringing for us, so the ring can be held until the late stage decides. The flag is static and app-wide, so `Ringer` is part of the dialer, not an optional blocker feature. Today Fossify has no ringing code at all (its call notification channel calls `setSound(null, null)`; Telecom rings).

`Ringer` obligations:

- Start on an incoming ringing call; stop on answer, disconnect, removal, and when the user silences (`InCallService.onSilenceRinger`, API 24).
- Honor `Call.EXTRA_SILENT_RINGING_REQUESTED` (API 29). This is how a screening-time silence or answer-and-hangup reaches a dialer that rings itself; ignoring it would ring loudly for calls we meant to silence.
- Respect ringer mode, Do Not Disturb (`matchesCallFilter` on API 33+; earlier levels need our own check), volume, the system or per-contact ringtone, and vibration.
- **Fail-safe:** ring by default. Any exception or missing state means ring now. There is no hold in v1 (the name is present at `onCallAdded`); the ring simply starts after the late decision, a few milliseconds later.
- **Implementation (decided by Phase 0):** a `Ringtone` (usage `NOTIFICATION_RINGTONE`, looping) plus vibrator, with our own ringer-mode and DND checks. The notification-channel alternative produced no sound on the test phone and was dropped.

Late actions: reject → `call.reject(false, null)` (verified: no ring, call gone ~240 ms later, platform logs it as `REJECTED_TYPE`, not blocked); silence → don't start (or stop) the ringer, incoming screen still answers; answer and hang up → answer, then disconnect. Nothing rings before the decision, so there is no leak.

### C. Coexistence with Fossify's blocking (D2)

Upstream `SimpleCallScreeningService` already blocks from the system blocked-number list, "block unknown" and "block hidden", and a package can bind only one screening service. Order in the hook: our early stage first; if it does not produce an explicit verdict, fall through to the upstream code unchanged. Upstream settings and UI stay as they are.

### D. Android version gating

Facts from the SDK's API data: `isEmergencyNumber`, `setSilenceCall`, `RoleManager`, `getCallDirection` are 29; Fossify's minSdk is 26. We do not raise minSdk (that edits a dependabot-churned line). The blocker is off below 29. Answer and hang up needs only the dialer role, because the dialer re-evaluates the same rules itself and needs no `Call.Details.getId()` handoff (API 35) between screening and the in-call service; without the dialer role it behaves as reject quietly. Gate with explicit `SDK_INT` checks, never try/catch.

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
| `services/CallService.kt` | `onCallAdded` after `super`: blocker/ringer hook that may claim the call and skip UI; `onSilenceRinger` override (import plus 6 lines) | 5 |
| `AndroidManifest.xml` | one contiguous block: `BlockerActivity` | 4 |
| `src/debug/AndroidManifest.xml` (ours, not upstream) | `IN_CALL_SERVICE_RINGING` meta-data on `CallService`, merged into debug builds only until the Phase 6 matrix passes; `Ringer` reads the merged manifest, so release builds leave ringing to Telecom | 5 |
| `res/menu/menu.xml`, `MainActivity.kt` | one "Call blocker" item and its handler (~4 lines) | 4 |
| `RecentCallsAdapter.kt` / recents fragment | blocked-row annotation and detail; seam chosen in its phase | 7 |

## 7. v1 scope (confirmed 2026-09-29)

The POC's feature list is not inherited. v1:

- **In:** contacts + emergency always allowed; ordered rules, allow and block; matchers exact number, prefix, no-number, name wildcard (CNAM), empty name; per-rule schedule Always or a custom window (days, start, end; may cross midnight, the start day owns the spill); actions reject quietly (default), reject, silence, answer and hang up (35+); timed pause (15 min / 1 h / until resume) with a resume notification; named schedules that pause blocking during their window (name, days, start, end, enabled); blocked-event log; screening-role setup and status; blocked annotation in Recents.
- **Deferred:** regex matchers (and their 50 ms time budget), the "no readable contacts → pause" safeguard and country override, folding runs of attempts in Recents, area-code/prefix "chips" from the call log.

## 8. Phases

Each phase ends with a green `./gradlew assembleFossDebug testFossDebugUnitTest` and an updated ledger.

**Phase 0 — Spikes (done 2026-09-29; results in the notes file)**
1. Ringing takeover: scratch build with `IN_CALL_SERVICE_RINGING=true`; confirm Telecom stops ringing; prototype both `Ringer` options; check `EXTRA_SILENT_RINGING_REQUESTED`, `onSilenceRinger`, DND and ringer modes, headset routing.
2. CNAM timing: caller name arrival versus `onCallAdded`/`onDetailsChanged` for labeled and unlabeled calls (a contact call was not tested); whether a name ever arrives late.
3. Cold start: kill the process, call, confirm the first call is screened.
- Exit met: Ringer approach chosen (`Ringtone` + vibrator), no settle window, empty-name semantics corrected. Untested items moved to Phase 6.

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

**Phase 5 — Dialer stage:** `Ringer`, late evaluation, name rules, answer-and-hangup handoff, `CallService` hook, manifest flag.
- Status 2026-09-29: implemented (140 unit tests; detekt and lint clean, no lint findings in new code). Pure `planDialer` and `ringPolicy` are tested; `telecom/BlockerCalls.kt` and `telecom/Ringer.kt` are the Android layer. `CallService` gained the hook and an `onSilenceRinger` override. The ringing flag is in the debug manifest only (verified absent from the merged release manifest).
- Design notes: no answer-and-hang-up handoff is needed (see §4.D); the dialer re-runs the whole list, so a call silenced at screening is recognised by `EXTRA_SILENT_RINGING_REQUESTED` and not recorded twice. A reject or answer-and-hang-up on the first look claims the call so Fossify never shows it; a later name change re-evaluates while ringing. Any failure rings the call and takes back the event. A 2-minute watchdog stops a ring that never got a stop callback.
- Device-verified on caiman (Android 17, debug build owning ringing): no rules rang and vibrated with the volume key silencing it; a `Likely*` quiet reject gave no ring, no screen and a logged event with the name; answer-and-hang-up answered (3 s call in the log) with nothing shown on the phone; a name silence rang nothing and stayed answerable; a screening-time number silence stayed quiet and was recorded once, not twice.
- Not yet tested: the full ringing matrix (Phase 6). A late reject is logged by the platform as rejected, not blocked, so it appears in Recents as an ordinary rejected call.
- Exit: a labeled call is blocked and logged; an unlabeled call rings; ringing works with the blocker off; a claimed handoff never shows the incoming screen.

**Phase 6 — Ringer parity hardening:** the full ringing matrix (ringer modes, DND modes, Bluetooth/headset, second call, lock screen, per-contact ringtones) before any release build carries the flag. Phase 0 covered only normal ringer mode with DND off.

**Phase 7 — Recents integration:** blocked annotation and detail dialog; "allow this number / add rule" actions.

**Phase 8 — Hardening and docs:** device matrix, `git merge upstream/main` rehearsal on a scratch branch, `FORK.md` ledger, `CONTEXT.md` glossary, ADRs (two-stage evaluation, owning the ringing, touch-point policy).

## 9. Decisions and open items

Settled:

- **D1 UI:** views, matching Fossify. Design each screen natively, not as a copy of the POC.
- **D2 Fossify's built-in blocking:** keep untouched, ours runs first (§4.C).
- **D3 Ring leak:** own the ringing (§4.B).
- **D4 Old app:** retired; no migration or export/import.

Open:


## 10. Risks

- **Ringing takeover is the biggest risk** (the prototype works in the normal case; the rest of the matrix is untested). A bug means missed calls in the app someone depends on. Mitigations: ring-by-default fail-safe, watchdog, Phase 6 parity matrix, and the flag lands only with a working `Ringer`.
- **Upstream drift:** if Fossify later adds its own ringing, `CallService` will double-ring after a merge. The ledger and merge rehearsal exist to catch this.
- **`CallService` hook** can regress the upstream call UI if the claim path skips setup wrongly; keep the skipped path tiny.
- **Recents seam** may need a bigger upstream edit than the ledger promises.
- **License:** Fossify Phone is GPL-3.0, so all code here is GPL-3.0; keep attribution intact.
