# Elbows Up Phone

<img alt="Elbows Up Phone icon" src="graphics/icon.webp" width="120" />

Elbows Up Phone is a fork of [Fossify Phone](https://github.com/FossifyOrg/Phone), a free and open-source phone app for Android. It keeps everything you expect from a dialer — calls, contacts, call history — and adds two things that make it worth switching to:

- 📞 **Caller ID names on incoming calls** — shows the name the carrier sends with a call: a business name, a caller's name, or a carrier-provided label such as "Likely Spam".
- 🛡️ **A built-in call blocker** — screens calls with Android's call screening service and blocks the ones you do not want, using rules you write.

Everything else — the dialer, contacts, call history, and the no-ads, no-tracking approach — comes from Fossify Phone and works the same way.

## Caller ID names on incoming calls

When someone calls, the carrier can send a name along with the number, separate from the name saved in your contacts. It may be a business name, a caller's name, or a carrier-provided label such as "Likely Spam". This is not only about spam calls: any name the carrier sends is shown. Many phones show this name on the incoming call screen. The plain Fossify Phone app did not.

Elbows Up Phone shows the carrier's name on the incoming call screen, next to the number. If the carrier sends no name, you just see the number, as before.

## Call screening and call blocking

Fossify Phone has its own simple blocker, and Elbows Up Phone leaves it in place. Elbows Up Phone adds a second, richer blocker on top of it:

| Blocker | How to open it | What it offers |
|---|---|---|
| Fossify's built-in | Settings → **Manage blocked numbers** | A list of blocked numbers, plus options to block calls from numbers not in contacts and from hidden numbers |
| Elbows Up Phone | Main menu (⋮) → **Call blocker** | Ordered rules, caller ID name matching, schedules, pauses, actions, and a blocked-call list |

Both blockers run together. Elbows Up Phone's rules are read first, and anything they do not decide falls through to Fossify's own checks, so nothing you set up in Fossify Phone is lost.

### Built on Android's call screening service

Elbows Up Phone plugs into Android's `CallScreeningService`, so it can look at a call while the system is still setting it up, before your phone rings. That is what lets it reject, silence, or answer and hang up a call without the ringtone ever starting. For the rules that depend on the caller ID name, the app also acts as your phone app, because the name only arrives once the call reaches the phone.

### What the blocker can do

You write an ordered list of rules, and Elbows Up Phone reads them from top to bottom. The first rule that matches a call decides what happens to it. Calls from your contacts and emergency numbers always ring.

A rule can match:

- an exact phone number
- a number that starts with certain digits
- private or hidden numbers
- a caller ID name (for example, a name containing "Spam")
- calls with no caller ID name

A block rule can:

- **Reject and hide** the call, so it does not appear as a missed call
- **Reject and show** the call as a missed call
- **Silence** the call: stop the ringing, but still let you answer
- **Answer and hang up**, so the caller does not reach your voicemail

And the rest:

- **Schedules** — pause blocking during set times, such as work hours
- **Timed pause** — turn blocking off for 15 minutes, an hour, or until you resume
- **Pause until next call** — let the next call through, then block again
- **A blocked-call list** — see what was blocked, why, and allow a number with one tap
- **Starter rules** — a fresh install begins with "Likely Spam" and "Likely Fraud" rules, which you can edit or delete like any other

Elbows Up Phone is careful with your calls. It works offline and does not use the internet. If anything is uncertain, the call rings: a call is only blocked when a rule you wrote says so.

## Download and setup

Download the newest build from the [Releases page](https://github.com/ccormier/elbowsup-phone/releases). It requires Android 8.0 or newer.

Setup takes a few steps the first time:

1. Set Elbows Up Phone as your phone app.
2. Give it the call screening role.
3. Allow it to read your contacts.

Blocking stays off until setup is complete. The app walks you through these steps and shows their status on the Call blocker screen.

## Keeping up with Fossify Phone

Elbows Up Phone is meant to grow together with its upstream project. When Fossify Phone publishes a new release, we merge it in and publish a matching Elbows Up Phone release. Our own changes are kept small and separate, so this stays easy to do.

Developers can read [FORK.md](FORK.md) for the merge process and build details.

## Issues and feature requests

Fossify Phone is the upstream project, and it is where most bugs should go. Before reporting anything, please check whether it also happens in the plain Fossify Phone app. You can install it from [Google Play](https://play.google.com/store/apps/details?id=org.fossify.phone), [F-Droid](https://f-droid.org/packages/org.fossify.phone/), or the [Fossify Phone releases page](https://github.com/FossifyOrg/Phone/releases/latest).

- **A bug that also happens in Fossify Phone** → report it to [Fossify Phone](https://github.com/FossifyOrg/Phone/issues). They own that code.
- **A bug that only happens in Elbows Up Phone** → report it to [Elbows Up Phone](https://github.com/ccormier/elbowsup-phone/issues). If it is not about caller ID names or blocking, it is probably a mistake in how we merged the latest upstream release, and we want to know.
- **A feature idea about caller ID names or blocking** → share it with [Elbows Up Phone](https://github.com/ccormier/elbowsup-phone/issues).
- **Any other feature idea** → share it with [Fossify Phone](https://github.com/FossifyOrg/Phone/issues). It belongs upstream, where everyone can use it.

## Donations

Elbows Up Phone is free, and we do not take donations. If you would like to support this kind of app, please [donate to the Fossify project](https://www.fossify.org/donate/). They build Fossify Phone, and this fork would not exist without them.

## Building from source

See [FORK.md](FORK.md) for the full instructions. In short: you need JDK 17 or newer and Android SDK platform 36, and you must build the patched Fossify Commons library first. Then run:

```bash
./gradlew assembleFossDebug
```

## Credits and license

- **[Fossify Phone](https://github.com/FossifyOrg/Phone)** — the app Elbows Up Phone is forked from, and the source of almost all of its code.
- **[Fossify Commons](https://github.com/FossifyOrg/Commons)** — the shared library that Fossify apps are built on.
- **[Fossify](https://www.fossify.org)** — visit the project on [GitHub](https://github.com/FossifyOrg), [Reddit](https://www.reddit.com/r/Fossify), or [Telegram](https://t.me/Fossify).
- Thanks to the Fossify contributors and translators who keep the upstream project going.

Elbows Up Phone is not affiliated with or endorsed by the Fossify project.

Elbows Up Phone keeps the upstream license, the [GNU General Public License v3.0](LICENSE). The original code remains the copyright of its authors.
