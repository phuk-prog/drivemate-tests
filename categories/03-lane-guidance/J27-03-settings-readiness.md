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

## Verified candidate retest — public run #76 (27 Oct 2026)
- [GitHub Actions run #76](https://github.com/phuk-prog/drivemate-tests/actions/runs/40560370610) completed **successfully**, including the Android emulator and `Every test passed` gates.
- Downloaded the run's `summary.json` artifact ID `12762065889` (no long logs). **268/268 passed**, `crashed: false`, `not_responding: false`.
- Exact app candidate commit tested: `4d710c66a41f71e091ebb88b7c95b9012d04450f`. Public test suite SHA reported: `753f4af84167f7cc2d1c9293e12f9b8c8bddac2a`.
- Both formerly blocking tests passed: `q3_overlappingSessionsCanReadTheSameSettingsWithoutDataStoreConflict` and `q3_3_6_7_27_completeJourneyAdvancesAndClearsLaneStrip`. Route-replacement check `q3_7_27_routeReplacementDoesNotReusePriorDecisionLanes` also passed.
- **Before / after:** original app run #75 = 266/268, confirmed duplicate DataStore exception plus session-ready timeout; candidate run #76 = 268/268. The frozen tests were unchanged for this comparison.
- Fix remains **draft app PR #42**, unmerged here. A successful synthetic/emulator journey is **not** evidence of visible, source-verified lanes during the owner's UK drive. Category 3 J27-01 stays reopened, and 3D/junction views remain separate open acceptance issues.
- Next acceptance: real Greater Manchester route with verified lane arrows, start-to-arrival replay, source-to-NavUi lane visibility at approach and passage, screenshot/emulator evidence; report unknown for incomplete source data, not a fabricated lane pass.
