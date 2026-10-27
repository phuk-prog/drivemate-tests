# Category 3 — session settings readiness, frozen baseline and retest (27 Oct 2026)

## Active item and scope
Current item only: `q3_3_6_7_27_completeJourneyAdvancesAndClearsLaneStrip` timed out at session `settingsReady` while preparing an Android lane-journey replay. That timeout happened **before** the app's lane strip could be asserted. This record does not close the separate user-reported absent lanes on a real Greater Manchester drive and does not score unrelated categories.

## Frozen baseline — original app, run #75
- Public [GitHub Actions run #75](https://github.com/phuk-prog/drivemate-tests/actions/runs/40559191088) tested the **unchanged app main** SHA `ceacb5120e69287887001020b00f378de04d1278` (not the candidate fix).
- **266/268 tests passed**. The existing `q3_3_6_7_27_completeJourneyAdvancesAndClearsLaneStrip` screen test again failed waiting for `settingsReady` (30-second ComposeTimeoutException).
- The **new focused pretest** `q3_overlappingSessionsCanReadTheSameSettingsWithoutDataStoreConflict` failed with `java.lang.IllegalStateException: There are multiple DataStores active for the same file: .../datastore/drivemate-settings.preferences_pb`.
- This is direct evidence of a repository lifecycle bug when settings are loaded from overlapping Android sessions. It is not proof that every earlier timeout had this cause, nor that lane guidance draws correctly.

## Narrow candidate repair and frozen retest
- [Draft app PR #42](https://github.com/phuk-prog/drivemate-app/pull/42) keeps only one Android Preferences DataStore instance alive per app process, using the shared `preferencesDataStore` delegate. The settings schema and lane guidance logic remain unchanged.
- The **same public branch** automatically selects the same-named candidate app branch when the public PR workflow runs. Both test methods and expected results are **unchanged** from the failing baseline. The next workflow run must test PR #42's exact candidate SHA and record `q3_overlappingSessionsCanReadTheSameSettingsWithoutDataStoreConflict` and the original lane journey separately.
- A red result still blocks merge. A green result proves settings startup for those tests, **not** route-accurate or visible lane guidance on real UK road data.

## Next targeted item
After a valid session-readiness retest, select one bounded route from the bundled real Greater Manchester graph with independently corroborated lane arrows, replay start-to-arrival, and check both `NavigationSession`'s published `nav.lanes` and the actual emulator-visible lane strip at approach, junction exit, and reroute. Mark missing legal/source evidence **unknown**, not fail or assumed lane preference. Reuse prior benchmarks only where relevant and lawfully observable; do not build an all-app test matrix.
