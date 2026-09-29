# Carrier label blocking — findings and work

2026-09-28. Device: caiman (Pixel 9 Pro, Android 17 / API 37), Rogers 302720 (CA).
Status: spike complete; probe code uncommitted; no feature code. This is input to a design, not a spec.

## Purpose

Block incoming calls the carrier labels "Likely Spam" / "Likely Fraud" — the only carrier signal this device receives. Two candidate bases: ElbowsUp as-is, or a Fossify Phone fork. This file carries the spike findings and the work each base needs, so a future session can pick up without the spike context.

## Verdict

The label is a **late label**: it appears in the dialer's `InCallService` while ringing, ~300 ms after the screening callback. The screening `Call.Details` is a **frozen snapshot** — no name, no extras, and immutable (proven by a 2 s hold with identical object hashes). Blocking on the label can therefore only be a **late pass** in the in-call service, never a screening decision, and only while the app holds the dialer role. The open UX cost is the **ring leak**: the call has already started ringing when the label arrives.

## Findings

Measured on a dozen test calls across two dialer configurations (ElbowsUp as dialer; Google Dialer as dialer with ElbowsUp screening):

| Observation | Result |
|---|---|
| Screening (`onScreenCall`) display name | `null`, presentation 0 (unset), extras 0, no account handle |
| Screening snapshot at 0.5 s / 1 s / 2 s | identical details and extras object hashes; never mutates |
| In-call (`onCallAdded`, ringing) display name | `"Likely Spam"`, presentation ALLOWED |
| STIR/SHAKEN (`callerNumberVerificationStatus`) | `NOT_VERIFIED` (0) at every phase of every call |
| Carrier extras | six system/IMS keys only (LTE, IMS/VoLTE, cross-SIM, DND, verification, IMS network type; audio codec added after answer) |
| Call log | label in `preferred_display_name`; `name` only when Google Dialer was the dialer; `asserted_display_name` always NULL |
| Label coverage | per-number and consistent: 226-220-1235 labeled on every call; 226-220-1234 never |
| Source | network, not Google Dialer: the label appeared in-call with ElbowsUp as the only dialer |

Notes:

- Google Dialer displays the label because it is the dialer and receives the same `onCallAdded` data — not because it has a different source.
- An AOSP Dialer check did not display the label; the system call path still carried it. Dialer UI behavior only, not a data difference.
- STIR/SHAKEN never produced PASSED/FAILED on Rogers, so it contributes nothing to this feature.

## Constraints

- **Fail open.** No label, no rule match, or engine error → the call rings normally.
- **Screening cannot use the label.** The frozen snapshot rules out a screening-time block; delaying the response does not help.
- **Dialer role required.** Screening-only deployments never receive the label.
- **Late block is not a silent reject.** Expect a ring leak; measure it before choosing the action.
- **Spec change required.** Ring-time decisions are outside the current binding spec; amend it before code.
- **Matching is user rules.** Labels are display-name text; reuse name matching rather than hardcoding label strings.

## Work

### Path A — implement in ElbowsUp

1. **Design + spec amendment.** Decide the late action (reject / silence + reject / take over ringing), matching, recording, and copy. Done when the binding spec has a reviewed ring-time decision section.
2. **Measure the ring leak.** Reinstall the probe, call from a labeled number, log `onCallAdded` → `disconnect()` timestamps, and listen. Done when the audible leak for the chosen action is known.
3. **Engine.** Reuse name matching; add tests for late matching and fail-open. Done when tests cover label match, absent label, and engine error.
4. **In-call service.** Run the late pass on `onCallAdded` (and `onDetailsChanged` as fallback), apply the action, record a blocked event. Done when a labeled call is blocked and recorded on device and unlabeled calls ring.
5. **Rule affordance.** Make the label rule creatable without docs (preset or hint in the rule sheet). Done when the rule can be made from the UI alone.
6. **Device pass.** Confirm contacts, pause, schedules, and emergency still win; restore roles and permissions.

Notes: `TelecomManager.silenceRinger()` is available to the dialer role. Taking over ringing (`IN_CALL_SERVICE_RINGING=true`) removes the leak but makes the app responsible for ringing every call — a fail-open risk to design for.

### Path B — Fossify Phone fork

Choose this only for a full dialer product; the label feature itself does not need a fork.

**License gate first:** Fossify Phone is GPL-3.0; ElbowsUp is Apache-2.0. A fork makes the combined work GPL-3.0. Decide before any work.

1. Fork and map `CallService` (InCallService) and `SimpleCallScreeningService` — its screening is a blocklist plus unknown/hidden-number checks; it never reads `callerDisplayName`.
2. Port `:engine` (pure Kotlin) and its tests.
3. Re-implement the call path in Fossify's services: cache-only screening, handoff, late pass.
4. Rebuild the rules UI in Fossify's view-based idiom (XML layouts; the Compose UI does not port).
5. Rewrite the spec; accept upstream merge burden on the call-path files.

## Open questions for the design

- Late action: reject, silence + reject, or take over ringing?
- Matching: reuse the existing name rules, or a dedicated label setting?
- Trigger points: `onCallAdded` only, or also `onDetailsChanged`?
- Recording: blocked event, and what the system call log shows (a late disconnect logs missed/rejected, not BLOCKED_TYPE).
- Race: the label arrives after the user answers — block anyway (answer-and-hangup) or let it be?
- Which allow paths override the late pass (contacts, pause, schedule, emergency)?

## Reproduce

- Probe (uncommitted): `app/src/main/kotlin/app/elbowsup/blocker/telecom/CallDetailsProbe.kt`, hooked into `ElbowsUpScreeningService.onScreenCall` and `ElbowsUpInCallService.onCallAdded`/`onDetailsChanged`; logcat tag `ElbowsUpProbe`.
- Build/install: see `AGENTS.md` (JDK export; caiman installs as user 0).
- Labeled test number: 226-220-1235 (Rogers); 226-220-1234 never labels.
- Capture: `adb -s <caiman> logcat -s ElbowsUpProbe`.
- Key APIs: `Call.Details.getCallerNumberVerificationStatus()` (API 30), `CallLog.Calls.PREFERRED_DISPLAY_NAME`, `TelecomManager.silenceRinger()` (dialer role).

## Current state

- Probe code is uncommitted in the working tree; revert or promote deliberately.
- Any probe build on caiman holds screening responses for 2 s — reinstall a clean build before normal use.
- The binding spec has no ring-time decision section yet.
