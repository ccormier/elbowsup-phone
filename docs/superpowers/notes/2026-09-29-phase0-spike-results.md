# Phase 0 spike results

2026-09-29. Device: caiman (Pixel 9 Pro, Android 17 / API 37), Rogers, user 0. The fork's debug build was the default dialer and screening app for the session. Roles and build were restored afterwards. Probe and ringer prototype code is saved outside the repo at `/Users/chrisc/workspace/elbowsup/docs/phase0-spike.patch` (never merged). The QA phone (cheetah) has no SIM and could not run any of this.

## Findings

| Question | Result |
|---|---|
| Caller name at screening | Always `null`, presentation 0, on every call. |
| Caller name at `onCallAdded` | Present on every call (8 of 8). Labeled number 226-220-1235: `"Likely Spam"`. Unlabeled 226-220-1234: **the number itself** (`"2262201234"`, presentation 1), not blank. |
| Time from screening to `onCallAdded` | 77–290 ms. The call is already `RINGING` (state 2) when added. |
| Name arriving later | Never. No `onDetailsChanged` between `onCallAdded` and disconnect on any call. |
| Cold process at screening | Yes. The first call after each install hit a process 0.9–1.1 s old, and screening ran normally. Later calls reused the warm process (procAge 74–368 s). |
| `IN_CALL_SERVICE_RINGING=true` | Telecom stopped ringing; our ringer was the only sound. |
| Ringer as `Ringtone` + vibrator | Works. Started 5 ms after `onCallAdded` (about 80 ms after screening). Ringer mode and DND read normally (`matchesCallFilter=true`). The volume button silenced it through `onSilenceRinger`. Stopped on answer/disconnect state. The user perceived a short delay before ringing, which is the cold-process spawn and screening wait that stock ringing also has. |
| Ringer as notification channel (sound, `USAGE_NOTIFICATION_RINGTONE`, insistent flag) | **Failed**: no sound and no notification, though the channel was created correctly. Cause not diagnosed. Dropped. |
| Screening-time silence (`setSilenceCall(true)`) with an owning ringer | `Call.EXTRA_SILENT_RINGING_REQUESTED` was `true` at `onCallAdded`. Our ringer honored it and stayed quiet. The incoming screen still appeared and could be answered. |
| Late block: `call.reject(false, null)` in `onCallAdded` for a labeled call | No ring, no incoming screen, call gone about 240 ms later (reject 3 ms after `onCallAdded`). Platform call log row: `type=5` (REJECTED), not the blocked type, with the label kept as `preferred_display_name`. |
| `Call.Details.getId()` | Worked on API 37 (`TC@n`). API 35+ only, per the SDK data. |

## Consequences for the design

- **"Empty name" must mean "no real name":** blank, or digits equal to the call's number. A blank-only test never matches on this carrier.
- **No settle window in v1.** The name is present when the call is added, so decide at `onCallAdded` and re-evaluate on `onDetailsChanged` while still ringing.
- **Ringer implementation is decided:** `Ringtone` + vibrator, with ringer-mode and DND checks, `onSilenceRinger`, and `EXTRA_SILENT_RINGING_REQUESTED`.
- **Owning the ringing removes the ring leak** for late blocks: nothing rings before the decision.
- **Not yet tested:** DND modes, Bluetooth/headset routing, second incoming call, lock screen, per-contact ringtones. These stay in Phase 6.
- **Sample size is small** (8 calls, one carrier, one phone). Re-check name timing on other calls during Phase 5.
