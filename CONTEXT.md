# Elbows Up

Elbows Up is the Fossify Phone dialer with a call blocker built in. The user writes an ordered list of rules, and calls that match a rule are rejected, silenced, or answered and hung up.

## Language

**Rule**:
One entry in the user's ordered list. The first rule that matches a call decides it, so order is priority.
_Avoid_: Filter, entry

**Block rule / Allow rule**:
A block rule acts on a matching call. An allow rule lets a matching call through and stops the walk, so an allow placed above a block rule wins.
_Avoid_: Whitelist, blacklist

**Contact**:
A number that belongs to someone in the user's own address book. Contacts and emergency numbers always ring, before any rule is read.
_Avoid_: Known number, trusted number

**Caller ID name**:
The name the carrier sends along with a call. It is not a contact name and may be wrong, missing, or just the number again.
_Avoid_: Caller name, CNAM label, contact name

**Real caller ID name**:
A caller ID name that is not blank, is allowed to be shown, and is not merely the digits of the caller's own number.
_Avoid_: Valid name

**Matcher**:
What a rule looks at: an exact number, the start of a number, a hidden number, a caller ID name pattern, or no caller ID name at all.
_Avoid_: Condition, filter type

**Action**:
What a block rule does to a matching call. There are four: reject and hide the missed call, reject and show the missed call, silence, and answer and hang up.
_Avoid_: Response, verdict

**Silence**:
Stop the ringing but leave the call answerable. It is the only action that does not end the call.
_Avoid_: Mute

**Answer and hang up**:
Pick the call up and end it at once, so the caller does not reach voicemail.
_Avoid_: Pick up and drop

## Deciding a call

**Early stage**:
The decision made while the call is being screened, before it rings. The caller ID name is not known yet, so only rules about the number can decide here.
_Avoid_: Screening pass, pre-ring check

**Late stage**:
The decision made when the call reaches the phone app, once the caller ID name is known. The whole rule list is read again.
_Avoid_: Dialer pass, second pass

**Fail open**:
When anything is uncertain or goes wrong, the call rings. A call is only blocked as the deliberate result of a matching rule.
_Avoid_: Fail safe

**Setup**:
The one-time grant of everything blocking needs: being the call screening app, being the phone app, and reading contacts. Blocking stays off until setup has completed once.

## Time

**Time window**:
A set of days with a start and an end time. It may cross midnight, and the day it starts on owns the part after midnight.
_Avoid_: Schedule (on a rule)

**Pause**:
Blocking switched off for a while: for 15 minutes, for an hour, or until the user resumes.
_Avoid_: Snooze, disable

**Pause schedule**:
A named, repeating time window during which blocking is paused, such as working hours.
_Avoid_: Quiet hours, do not disturb (that is Android's own feature)

## What the user sees

**Blocked call**:
A call that a rule acted on, recorded with the number, the caller ID name, the action and a one-line description of the rule.
_Avoid_: Spam call, filtered call

**Blocked-call list**:
The most recent blocked calls, shown on the blocker screen, where a number can be allowed.
_Avoid_: History, log (the log is the stored record)
