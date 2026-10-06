# Blocking requires the screening role, even as the phone app

Elbows Up Phone is the phone app, so it looks as if asking for the call screening role as well is redundant: with nobody holding the role, Telecom falls back to the phone app's screening service and number rules block before the call rings. We ask for it anyway. Telecom binds exactly one screening app: the user's chosen one if it is a different app from the phone app, otherwise the phone app. If any other caller-ID or spam app holds the role, our early stage is never bound, and an app cannot see who else holds a role, only whether it holds it. Holding the role is the one thing that guarantees our early stage runs, so blocking is off at both stages without it, and the status screen says so.

Confirmed on a device: with Google's Phone app holding the role, Telecom bound its screening service and not ours, and a rule that should have blocked did not. With nobody holding it, our service was bound and blocked. Both stages now check the role, so the status screen and the behaviour agree.

## Considered Options

- **Drop the role and rely on the phone app's screening service.** Rejected: it works until the user has another caller-ID app chosen, then early blocking silently stops while the screen says "Active".
- **Count the role as optional and show a partial status.** Rejected: the app cannot tell which case it is in.
