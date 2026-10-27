# Category 3 — Spoken lanes, warnings and audio interruption acceptance

Date: 27 October 2026 (UK).

## Before-change finding

The production `LaneAlerts.warning` used to return the identical phrase for the
first and every materially changed roadworks report. `VoiceMessageQueue` has a
30-second identical-alert suppression window, so a changed report could be
suppressed even though its event ID/contents changed. This is an evidence-based
static diagnosis of an interaction between two production paths, not a claim
that a physical audio failure was observed.

## Scoped correction

`drivemate-app` same-named branch differentiates the subsequent updated
roadworks phrase (`Roadworks update. Follow road signs.`) from the original
(`Roadworks ahead. Follow road signs.`). Missing or unchanged roadworks
reports are still silent. Warnings retain the owner's follow-signs instruction,
do not invent temporary lane layouts and do not interrupt imminent manoeuvres.

## Combined public testing

- `LaneSpeechInterruptionAcceptanceTest.kt`: production voice queue message
  ordering, urgent turn priority, warning continuation only once, suppression
  of old manoeuvres on reroute, source-event change versus duplicate, sound
  modes and the default duck-not-pause Bluetooth/media focus policy.
- `LaneSpeechSessionScreenTest.kt`: production Android NavigationSession
  computes one lane prompt per visible junction and does not speak it again
  on repeated GPS updates, does not make up missing/unreliable lane advice,
  advances to another junction and allows a new route to reuse a decision ID.
- All tests run in the public `drivemate-tests` Actions workflow against
  the same-named private `drivemate-app` branch.
- Report results only from the GitHub run summary, **not** from this plan.

## Limits and acceptance

This exercises the real policy/queue and emulator session, but does **not**
capture the sound from a car/phone, measure audio ducking levels, validate
Bluetooth focus handoff or test actual speech-recognition quality. Physical
road/car tests are excluded; the practical Bluetooth/output checks remain
unverified. Category 3 remains Pending until all required non-physical
acceptance items, including early-split review and parked approval, are proven.
No signed APK is built or released by this work.
