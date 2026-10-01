# Decide a call in two stages

The carrier's caller ID name is never visible while a call is being screened, and only shows up about 100 to 300 ms later in the phone app. A rule that looks at the name, or "no name", cannot be decided early: a "no name" rule evaluated at screening would match every call from a non-contact. So the same ordered rule list is read twice, once at screening with the number only, and again in the phone app with the name filled in. Rules about the number act early and can reject before the call rings; rules about the name can only act late. This is why Elbows Up has to be the phone app, not just a call screening app.

## Considered Options

- **Screening only, as a standalone blocker.** Rejected: it can never use the caller ID name, which is the most useful signal the carrier gives (it is what labels a call "Likely Spam").
- **Wait for the name before ringing.** Rejected: the name arrives in the phone app, which Telecom has already started ringing for, and a hold would mean owning the ringing (see ADR 0002).
