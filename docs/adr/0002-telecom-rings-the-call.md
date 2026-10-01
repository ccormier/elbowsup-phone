# Let Telecom ring incoming calls

We built our own ringer (declaring that the phone app owns ringing) so a late rejection would never be heard, then removed it. In Priority-only Do Not Disturb with starred contacts allowed, which is what Android's default Sleeping mode uses, Android mutes ringtone audio and vibration for every app except four system packages, and a phone app cannot join that list. Our ringer correctly decided a starred contact should ring, then played into that mute, so a starred contact would not have rung at night. Telecom's own ringing does not have the problem and also owns Do Not Disturb, ringer modes, Bluetooth, second calls and ringtones.

Telecom holds its ringtone about a second for the phone app to get ready, so a rejection that arrives about half a second after the call is never heard. A late silence is the one place this is timing-based: Telecom ignores a silence sent before it starts ringing, so the phone app repeats it until the ring has stopped.

## Considered Options

- **Own the ringing and switch it off under Do Not Disturb.** Not built: the ringing flag is read when a call arrives, so it could work with two switchable services, but it is a workaround we would own forever.
- **Play the ring as alarm-volume audio.** Not built: it follows the alarm volume, and total silence would still mute it.
